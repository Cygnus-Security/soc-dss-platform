# Metasploitable2 SOAR Response Lab

This lab proves that approving `temporary_ip_block` in SOC-DSS causes SOAR to
change connectivity to an intentionally vulnerable target.

## Safety boundary

Metasploitable2 is intentionally vulnerable. The lab publishes none of its
ports to the Ubuntu host. Two `internal` Docker networks isolate it from the
LAN and Internet:

- `172.31.252.0/24`: test client (`.10`) and victim (`.20`).
- `172.31.253.0/29`: SOAR response server (`.2`) and firewall executor (`.3`).

The firewall executor shares the victim network namespace. It has only
`NET_ADMIN`, accepts action targets only from configured RFC1918 CIDRs, denies
the SOAR control subnet even when it overlaps an allowed CIDR, and automatically
removes each rule after the configured TTL. The victim cannot initiate a
connection toward the SOAR control address.

The pinned community container is Linux/AMD64 and is old by design. Rapid7
publishes Metasploitable2 as a deliberately vulnerable VM; the Docker image is
a community packaging of that environment. Use this lab only on an isolated
test host.

## 1. Configure live lab execution

Generate a token. Copy the printed value; `.env` files do not execute command
substitution.

```bash
openssl rand -hex 32
```

Edit `deploy/production/.env`:

```env
SOAR_EXECUTION_MODE=live
SOAR_ACTION_WEBHOOK_URL=http://172.31.253.3:9000/v1/actions
SOAR_ACTION_WEBHOOK_TOKEN=<paste-the-generated-token>
METASPLOITABLE_BLOCK_TTL_SECONDS=120
METASPLOITABLE_ALLOWED_TARGET_CIDRS=10.0.0.0/8,172.16.0.0/12,192.168.0.0/16
METASPLOITABLE_MAX_ACTIVE_BLOCKS=200
```

Keep `ACTION_APPROVAL_SECRET` unchanged and identical between SOC-DSS and SOAR.
The CIDR allowlist accepts private dataset source addresses for bulk-live
workflow demonstrations. Every rule is applied only inside the isolated victim
network namespace. The control subnet is always denied, and public IP ranges
cannot be configured. Only the dedicated `172.31.252.10` client provides the
end-to-end connectivity assertion described below.

If either lab subnet overlaps an existing Docker network, select two unused
subnets and update every corresponding address in the same `.env` file.

## 2. Start the victim and connect SOAR

The normal SOC-DSS and SOAR integration stack must already be running.

```bash
make metasploitable2-lab-up
docker ps --format '{{.Names}}\t{{.Status}}' | grep soc-dss-metasploitable2
```

The first run downloads an approximately 603 MB compressed AMD64 image.

Verify the executor and SOAR configuration:

```bash
docker logs --tail 20 soc-dss-metasploitable2-firewall
docker exec mcp_response_server sh -lc '
  echo "MODE=$SOAR_EXECUTION_MODE"
  echo "WEBHOOK=$SOAR_ACTION_WEBHOOK_URL"
'
```

Expected values are `MODE=live` and the `.3:9000/v1/actions` lab URL.

## 3. Establish the precondition

The test client must initially reach Apache on the victim:

```bash
make metasploitable2-lab-probe
```

Expected result:

```text
PASS: test client can reach Metasploitable2
```

## 4. Create a lab incident

The seed script sends five controlled Wazuh-format alerts through the real
ingestion gateway. It does not claim that a real Wazuh sensor observed them.

```bash
export INGESTION_API_TOKEN='<the configured ingestion token>'
make metasploitable2-lab-seed
```

Wait for correlation to finish. In the UI, select the incident with:

```text
Source IP:    172.31.252.10
Target Asset: soc-dss-metasploitable2-victim
```

If an older large import is still being correlated, wait for it to complete
and run **Correlate Alerts** once more.

## 5. Approve and prove containment

In the incident detail:

1. Select **Request Temporary IP Block**.
2. Approve the new `PENDING_APPROVAL` action.
3. Wait up to 15 seconds for the SOAR polling loop.

Inspect the rule:

```bash
make metasploitable2-lab-rules
```

The output must include a rule for `172.31.252.10` with a
`soc-dss-lab:172.31.252.10` comment.

The blocked-connectivity assertion must now pass:

```bash
make metasploitable2-lab-probe-blocked
```

Expected result while the block is active:

```text
PASS: SOAR block prevents the test client from reaching Metasploitable2
```

SOC-DSS should record the action as `EXECUTED`. The executor audit is available
without exposing the victim:

```bash
tail -n 10 deploy/metasploitable2-lab/runtime/firewall/firewall-audit.jsonl
```

After the TTL expires, `make metasploitable2-lab-probe` must pass again and the
audit must contain an `UNBLOCK` event.

### Bulk-live workflow

The same isolated executor can hold up to
`METASPLOITABLE_MAX_ACTIVE_BLOCKS` unique private source-IP rules. Select up to
100 incidents whose source IPs are within `METASPLOITABLE_ALLOWED_TARGET_CIDRS`,
enter the bulk rationale, and dispatch them from the incident list. The SOAR
host consumes the actions sequentially; the default batch size is 50.

Monitor progress and count active managed rules with:

```bash
make soar-action-logs
docker exec soc-dss-metasploitable2-firewall \
  sh -lc "iptables -S INPUT | grep -c 'soc-dss-lab:'"
tail -n 100 deploy/metasploitable2-lab/runtime/firewall/firewall-audit.jsonl
```

`make soar-action-logs` combines the three execution stages and prefixes every
line with its container name:

- `mcp_soar_host`: Kafka polling, sequential batch position, execution duration,
  result publication, throttle delay, and offset commit.
- `mcp_response_server`: approval validation, idempotency, adapter mode, gateway
  HTTP status/body, audit write, and final action status.
- `soc-dss-metasploitable2-firewall`: request receipt, rejection reason, rule
  installation, active-rule count, and TTL expiry/removal.

Set `SOAR_LOG_SINCE=30m` to include an earlier window. Approval tokens, webhook
tokens, and signing secrets are never included in these logs.

An `EXECUTED` result proves that the rule was installed in the isolated victim
namespace. Only the dedicated test client provides an observable connectivity
test; imported dataset addresses normally have no corresponding live client in
the lab.

## 6. Stop the lab

```bash
make metasploitable2-lab-down
```

Then restore the safe default in `deploy/production/.env`:

```env
SOAR_EXECUTION_MODE=dry-run
SOAR_ACTION_WEBHOOK_URL=
SOAR_ACTION_WEBHOOK_TOKEN=
```

Recreate the response server after changing the file:

```bash
docker compose \
  --env-file deploy/production/.env \
  -f integrations/soar-mcp-platform/docker-compose.yml \
  up -d --no-deps --force-recreate response_server
```
