#!/usr/bin/env bash
set -euo pipefail

API_BASE=${INGESTION_API_BASE:-http://localhost:8090}
ATTACKER_IP=${METASPLOITABLE_ATTACKER_IP:-172.31.252.10}
VICTIM_IP=${METASPLOITABLE_VICTIM_IP:-172.31.252.20}
TOKEN=${INGESTION_API_TOKEN:-}
RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
PAYLOAD=$(mktemp)
trap 'rm -f "$PAYLOAD"' EXIT

for sequence in 1 2 3 4 5; do
  printf '%s\n' "{\"id\":\"metasploitable2-${RUN_ID}-${sequence}\",\"timestamp\":\"${NOW}\",\"rule\":{\"id\":\"10090${sequence}\",\"level\":15,\"description\":\"Metasploitable2 lab brute force detected\",\"groups\":[\"authentication_failed\",\"brute_force\"],\"mitre\":{\"id\":[\"T1110\"],\"tactic\":[\"Credential Access\"],\"technique\":[\"Brute Force\"]}},\"agent\":{\"id\":\"ms2-lab\",\"name\":\"soc-dss-metasploitable2-victim\",\"ip\":\"${VICTIM_IP}\"},\"data\":{\"srcip\":\"${ATTACKER_IP}\",\"dstip\":\"${VICTIM_IP}\"},\"full_log\":\"Failed password for msfadmin from ${ATTACKER_IP} to ${VICTIM_IP}\"}" >> "$PAYLOAD"
done

AUTH_HEADER=()
if [[ -n "$TOKEN" ]]; then
  AUTH_HEADER=(-H "Authorization: Bearer ${TOKEN}")
fi

echo "Sending five controlled Wazuh-format alerts for ${ATTACKER_IP} -> ${VICTIM_IP}"
curl --fail-with-body --silent --show-error \
  -X POST \
  "${AUTH_HEADER[@]}" \
  "${API_BASE}/v1/files?source_product=wazuh&mode=immediate&dataset_id=metasploitable2-${RUN_ID}&resume=false" \
  -F "file=@${PAYLOAD};filename=metasploitable2-lab.jsonl"
echo
echo "Alerts accepted. Wait for normalization/correlation, then select the incident with source IP ${ATTACKER_IP}."
