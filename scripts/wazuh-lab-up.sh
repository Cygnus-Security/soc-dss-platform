#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
ENV_FILE="${ROOT_DIR}/deploy/wazuh-lab/.env"

if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

WAZUH_VERSION=${WAZUH_VERSION:-v4.14.5}
WAZUH_LAB_ROOT=${WAZUH_LAB_ROOT:-deploy/wazuh-lab/runtime}
WAZUH_DASHBOARD_HTTP_PORT=${WAZUH_DASHBOARD_HTTP_PORT:-80}

if [[ "${WAZUH_LAB_ROOT}" != /* ]]; then
  WAZUH_LAB_ROOT="${ROOT_DIR}/${WAZUH_LAB_ROOT}"
fi

REPO_DIR="${WAZUH_LAB_ROOT}/wazuh-docker"
COMPOSE_DIR="${REPO_DIR}/single-node"
CERT_DIR="${COMPOSE_DIR}/config/wazuh_indexer_ssl_certs"
DASHBOARD_COMPOSE_FILE="${COMPOSE_DIR}/docker-compose.yml"
DASHBOARD_CONFIG_FILE="${COMPOSE_DIR}/config/wazuh_dashboard/opensearch_dashboards.yml"

if [[ "$(uname -s)" == "Linux" ]]; then
  MAX_MAP_COUNT=$(sysctl -n vm.max_map_count 2>/dev/null || echo 0)
  if [[ "${MAX_MAP_COUNT}" -lt 262144 ]]; then
    echo "vm.max_map_count is ${MAX_MAP_COUNT}; Wazuh Indexer requires at least 262144."
    echo "Run: sudo sysctl -w vm.max_map_count=262144"
  fi
fi

mkdir -p "${WAZUH_LAB_ROOT}"

if [[ ! -d "${REPO_DIR}/.git" ]]; then
  git clone --depth 1 --branch "${WAZUH_VERSION}" https://github.com/wazuh/wazuh-docker.git "${REPO_DIR}"
else
  echo "Using existing Wazuh Docker checkout: ${REPO_DIR}"
fi

# The upstream single-node stack publishes an HTTPS dashboard on host port 443.
# This lab intentionally exposes the dashboard over plain HTTP. Keep the
# manager-to-indexer connection encrypted; only the browser-facing dashboard
# listener is changed here.
if grep -q '\${WAZUH_DASHBOARD_HTTP_PORT:-80}:5601' "${DASHBOARD_COMPOSE_FILE}"; then
  :
elif grep -Eq '^[[:space:]]*-[[:space:]]*"?443:5601"?[[:space:]]*$' "${DASHBOARD_COMPOSE_FILE}"; then
  sed -i.bak -E \
    's|^([[:space:]]*)-[[:space:]]*"?443:5601"?[[:space:]]*$|\1- "${WAZUH_DASHBOARD_BIND_ADDRESS:-0.0.0.0}:${WAZUH_DASHBOARD_HTTP_PORT:-80}:5601"|' \
    "${DASHBOARD_COMPOSE_FILE}"
  rm -f "${DASHBOARD_COMPOSE_FILE}.bak"
else
  echo "Unable to locate the Wazuh dashboard port mapping in ${DASHBOARD_COMPOSE_FILE}." >&2
  exit 1
fi

if grep -q '^server.ssl.enabled: true$' "${DASHBOARD_CONFIG_FILE}"; then
  sed -i.bak 's/^server.ssl.enabled: true$/server.ssl.enabled: false/' "${DASHBOARD_CONFIG_FILE}"
  rm -f "${DASHBOARD_CONFIG_FILE}.bak"
elif ! grep -q '^server.ssl.enabled: false$' "${DASHBOARD_CONFIG_FILE}"; then
  echo "Unable to configure HTTP in ${DASHBOARD_CONFIG_FILE}." >&2
  exit 1
fi

if ! grep -q '^opensearch_security.cookie.secure: false$' "${DASHBOARD_CONFIG_FILE}"; then
  printf '\nopensearch_security.cookie.secure: false\n' >> "${DASHBOARD_CONFIG_FILE}"
fi

cd "${COMPOSE_DIR}"

if [[ ! -f "${CERT_DIR}/admin.pem" ]]; then
  docker compose -f generate-indexer-certs.yml run --rm generator
fi

docker compose up -d

echo "Wazuh lab is starting."
echo "Dashboard: http://localhost:${WAZUH_DASHBOARD_HTTP_PORT}"
echo "Credentials: admin / SecretPassword"
