#!/usr/bin/env bash
set -euo pipefail

since="${SOAR_LOG_SINCE:-5m}"
containers=(
  mcp_soar_host
  mcp_response_server
  soc-dss-metasploitable2-firewall
)
pids=()

cleanup() {
  for pid in "${pids[@]:-}"; do
    kill "${pid}" 2>/dev/null || true
  done
}
trap cleanup EXIT INT TERM

for container in "${containers[@]}"; do
  if ! docker inspect "${container}" >/dev/null 2>&1; then
    echo "Missing container: ${container}" >&2
    exit 1
  fi
done

echo "Following SOAR action logs since ${since}. Press Ctrl+C to stop."
for container in "${containers[@]}"; do
  docker logs --since "${since}" --follow "${container}" 2>&1 \
    | sed -u "s/^/[${container}] /" &
  pids+=("$!")
done

wait "${pids[@]}"
