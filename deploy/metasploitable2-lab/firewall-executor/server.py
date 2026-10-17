import hashlib
import hmac
import ipaddress
import json
import os
import subprocess
import threading
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


LISTEN_PORT = 9000
TOKEN = os.environ.get("ACTION_WEBHOOK_TOKEN", "")
ALLOWED_TARGET_IP = ipaddress.ip_address(
    os.environ.get("ALLOWED_TARGET_IP", "172.31.252.10")
)
SOAR_CONTROL_IP = ipaddress.ip_address(
    os.environ.get("SOAR_CONTROL_IP", "172.31.253.2")
)
CONTROL_SUBNET = ipaddress.ip_network(
    os.environ.get("CONTROL_SUBNET", "172.31.253.0/29"), strict=True
)
BLOCK_TTL_SECONDS = max(10, min(int(os.environ.get("BLOCK_TTL_SECONDS", "120")), 3600))
AUDIT_FILE = Path(os.environ.get("ACTION_AUDIT_FILE", "/app/data/firewall-audit.jsonl"))

_lock = threading.Lock()
_timers: dict[str, threading.Timer] = {}


def _run_iptables(*arguments: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["iptables", "-w", "5", *arguments],
        check=check,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=10,
    )


def _rule(target: str) -> list[str]:
    return [
        "-s", target,
        "-m", "comment", "--comment", f"soc-dss-lab:{target}",
        "-j", "DROP",
    ]


def _ensure_rule(chain: str, rule: list[str], position: str = "1") -> None:
    if _run_iptables("-C", chain, *rule, check=False).returncode != 0:
        _run_iptables("-I", chain, position, *rule)


def _delete_rule(chain: str, rule: list[str]) -> bool:
    if _run_iptables("-C", chain, *rule, check=False).returncode != 0:
        return False
    _run_iptables("-D", chain, *rule)
    return True


def _audit(event: dict) -> None:
    AUDIT_FILE.parent.mkdir(parents=True, exist_ok=True)
    record = {
        "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        **event,
    }
    with AUDIT_FILE.open("a", encoding="utf-8") as stream:
        stream.write(json.dumps(record, separators=(",", ":")) + "\n")


def _protect_control_plane() -> None:
    allow_webhook = [
        "-s", str(SOAR_CONTROL_IP), "-p", "tcp", "--dport", str(LISTEN_PORT),
        "-m", "comment", "--comment", "soc-dss-lab:allow-soar-webhook",
        "-j", "ACCEPT",
    ]
    drop_control_input = [
        "-s", str(CONTROL_SUBNET),
        "-m", "comment", "--comment", "soc-dss-lab:isolate-control-input",
        "-j", "DROP",
    ]
    allow_control_reply = [
        "-d", str(SOAR_CONTROL_IP), "-m", "conntrack", "--ctstate", "ESTABLISHED,RELATED",
        "-m", "comment", "--comment", "soc-dss-lab:allow-control-reply",
        "-j", "ACCEPT",
    ]
    reject_control_output = [
        "-d", str(CONTROL_SUBNET),
        "-m", "comment", "--comment", "soc-dss-lab:isolate-control-output",
        "-j", "REJECT",
    ]
    _ensure_rule("INPUT", drop_control_input, "1")
    _ensure_rule("INPUT", allow_webhook, "1")
    _ensure_rule("OUTPUT", reject_control_output, "1")
    _ensure_rule("OUTPUT", allow_control_reply, "1")


def _unblock(target: str, action_id: str) -> None:
    with _lock:
        removed = _delete_rule("INPUT", _rule(target))
        _timers.pop(target, None)
        _audit({
            "event": "UNBLOCK",
            "action_id": action_id,
            "target": target,
            "removed": removed,
        })


def _block(target: str, action_id: str) -> dict:
    with _lock:
        rule = _rule(target)
        already_present = _run_iptables("-C", "INPUT", *rule, check=False).returncode == 0
        if not already_present:
            _run_iptables("-I", "INPUT", "1", *rule)

        previous = _timers.pop(target, None)
        if previous:
            previous.cancel()
        timer = threading.Timer(BLOCK_TTL_SECONDS, _unblock, args=(target, action_id))
        timer.daemon = True
        _timers[target] = timer
        timer.start()

        expires_at = datetime.now(timezone.utc) + timedelta(seconds=BLOCK_TTL_SECONDS)
        _audit({
            "event": "BLOCK",
            "action_id": action_id,
            "target": target,
            "ttl_seconds": BLOCK_TTL_SECONDS,
            "already_present": already_present,
            "expires_at": expires_at.isoformat().replace("+00:00", "Z"),
        })
        return {
            "status": "EXECUTED",
            "message": f"Blocked {target} from Metasploitable2 for {BLOCK_TTL_SECONDS} seconds",
            "target": target,
            "ttl_seconds": BLOCK_TTL_SECONDS,
            "expires_at": expires_at.isoformat().replace("+00:00", "Z"),
            "already_present": already_present,
        }


class Handler(BaseHTTPRequestHandler):
    server_version = "SocDssMetasploitableFirewall/1.0"

    def _send(self, status: int, body: dict) -> None:
        encoded = json.dumps(body, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self) -> None:
        if self.path != "/health":
            self._send(404, {"error": "not_found"})
            return
        self._send(200, {
            "status": "UP",
            "allowed_target": str(ALLOWED_TARGET_IP),
            "ttl_seconds": BLOCK_TTL_SECONDS,
        })

    def do_POST(self) -> None:
        if self.path != "/v1/actions":
            self._send(404, {"error": "not_found"})
            return
        supplied = self.headers.get("Authorization", "")
        expected = f"Bearer {TOKEN}"
        if not TOKEN or not hmac.compare_digest(supplied, expected):
            self._send(401, {"error": "invalid_token"})
            return
        try:
            content_length = int(self.headers.get("Content-Length", "0"))
            if content_length < 1 or content_length > 16_384:
                raise ValueError("invalid_content_length")
            request = json.loads(self.rfile.read(content_length))
            if request.get("action_type") != "temporary_ip_block":
                raise ValueError("action_not_supported")
            target = ipaddress.ip_address(str(request.get("target", "")))
            if target.version != 4 or target != ALLOWED_TARGET_IP:
                raise ValueError("target_not_allowed_in_lab")
            action_id = str(request.get("action_id", ""))
            if not action_id or len(action_id) > 128:
                raise ValueError("invalid_action_id")
            self._send(200, _block(str(target), action_id))
        except ValueError as error:
            self._send(400, {"error": str(error)})
        except Exception as error:
            error_id = hashlib.sha256(repr(error).encode()).hexdigest()[:12]
            _audit({"event": "ERROR", "error_id": error_id, "error": str(error)})
            self._send(500, {"error": "execution_failed", "error_id": error_id})

    def log_message(self, format_string: str, *arguments) -> None:
        print(f"[HTTP] {self.address_string()} {format_string % arguments}", flush=True)


if __name__ == "__main__":
    if not TOKEN:
        raise SystemExit("ACTION_WEBHOOK_TOKEN is required")
    _protect_control_plane()
    print(
        f"[READY] webhook=:9000 target={ALLOWED_TARGET_IP} ttl={BLOCK_TTL_SECONDS}s",
        flush=True,
    )
    ThreadingHTTPServer(("0.0.0.0", LISTEN_PORT), Handler).serve_forever()
