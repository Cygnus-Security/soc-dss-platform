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
RFC1918_NETWORKS = tuple(
    ipaddress.ip_network(value)
    for value in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")
)


def _is_rfc1918_address(target: ipaddress.IPv4Address) -> bool:
    return any(target in network for network in RFC1918_NETWORKS)


def _parse_allowed_target_cidrs(value: str) -> tuple[ipaddress.IPv4Network, ...]:
    networks = []
    for entry in (item.strip() for item in value.split(",")):
        if not entry:
            continue
        network = ipaddress.ip_network(entry, strict=True)
        if network.version != 4 or not any(
            network.subnet_of(private_network) for private_network in RFC1918_NETWORKS
        ):
            raise ValueError(f"ALLOWED_TARGET_CIDRS contains non-RFC1918 network: {entry}")
        networks.append(network)
    if not networks:
        raise ValueError("ALLOWED_TARGET_CIDRS must contain at least one RFC1918 IPv4 network")
    return tuple(networks)


ALLOWED_TARGET_IP = ipaddress.ip_address(
    os.environ.get("ALLOWED_TARGET_IP", "172.31.252.10")
)
if ALLOWED_TARGET_IP.version != 4 or not _is_rfc1918_address(ALLOWED_TARGET_IP):
    raise ValueError("ALLOWED_TARGET_IP must be an RFC1918 IPv4 address")
ALLOWED_TARGET_CIDRS = _parse_allowed_target_cidrs(
    os.environ.get(
        "ALLOWED_TARGET_CIDRS",
        "10.0.0.0/8,172.16.0.0/12,192.168.0.0/16",
    )
)
SOAR_CONTROL_IP = ipaddress.ip_address(
    os.environ.get("SOAR_CONTROL_IP", "172.31.253.2")
)
CONTROL_SUBNET = ipaddress.ip_network(
    os.environ.get("CONTROL_SUBNET", "172.31.253.0/29"), strict=True
)
BLOCK_TTL_SECONDS = max(10, min(int(os.environ.get("BLOCK_TTL_SECONDS", "120")), 3600))
MAX_ACTIVE_BLOCKS = max(1, min(int(os.environ.get("MAX_ACTIVE_BLOCKS", "200")), 1000))
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


def _target_allowed(
    target: ipaddress.IPv4Address | ipaddress.IPv6Address,
) -> bool:
    if target.version != 4 or target in CONTROL_SUBNET:
        return False
    return target == ALLOWED_TARGET_IP or any(
        target in network for network in ALLOWED_TARGET_CIDRS
    )


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
        if target not in _timers and len(_timers) >= MAX_ACTIVE_BLOCKS:
            raise ValueError("active_block_limit_reached")
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
        with _lock:
            active_blocks = len(_timers)
        self._send(200, {
            "status": "UP",
            "allowed_target": str(ALLOWED_TARGET_IP),
            "allowed_target_cidrs": [str(network) for network in ALLOWED_TARGET_CIDRS],
            "denied_control_subnet": str(CONTROL_SUBNET),
            "active_blocks": active_blocks,
            "max_active_blocks": MAX_ACTIVE_BLOCKS,
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
        request = {}
        try:
            content_length = int(self.headers.get("Content-Length", "0"))
            if content_length < 1 or content_length > 16_384:
                raise ValueError("invalid_content_length")
            request = json.loads(self.rfile.read(content_length))
            if not isinstance(request, dict):
                raise ValueError("invalid_request")
            if request.get("action_type") != "temporary_ip_block":
                raise ValueError("action_not_supported")
            target = ipaddress.ip_address(str(request.get("target", "")))
            if not _target_allowed(target):
                raise ValueError("target_not_allowed_in_lab")
            action_id = str(request.get("action_id", ""))
            if not action_id or len(action_id) > 128:
                raise ValueError("invalid_action_id")
            self._send(200, _block(str(target), action_id))
        except ValueError as error:
            _audit({
                "event": "REJECTED",
                "action_id": str(request.get("action_id", "")),
                "target": str(request.get("target", "")),
                "reason": str(error),
            })
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
        f"[READY] webhook=:9000 target={ALLOWED_TARGET_IP} "
        f"cidrs={','.join(str(network) for network in ALLOWED_TARGET_CIDRS)} "
        f"max_active={MAX_ACTIVE_BLOCKS} ttl={BLOCK_TTL_SECONDS}s",
        flush=True,
    )
    ThreadingHTTPServer(("0.0.0.0", LISTEN_PORT), Handler).serve_forever()
