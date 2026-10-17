#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
INTEGRATION_ENV=${INTEGRATION_ENV:-deploy/production/.env}
WAZUH_ENV_FILE="${ROOT_DIR}/deploy/wazuh-lab/.env"
LAB_COMPOSE="${ROOT_DIR}/deploy/metasploitable2-lab/docker-compose.yml"

if [[ "${INTEGRATION_ENV}" != /* ]]; then
  INTEGRATION_ENV="${ROOT_DIR}/${INTEGRATION_ENV}"
fi

if [[ ! -f "${INTEGRATION_ENV}" ]]; then
  INTEGRATION_ENV="${ROOT_DIR}/deploy/production/.env.example"
fi

export SOAR_ACTION_WEBHOOK_TOKEN=${SOAR_ACTION_WEBHOOK_TOKEN:-wazuh-profile-unused}

if [[ -f "${WAZUH_ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${WAZUH_ENV_FILE}"
  set +a
fi

WAZUH_LAB_ROOT=${WAZUH_LAB_ROOT:-deploy/wazuh-lab/runtime}
if [[ "${WAZUH_LAB_ROOT}" != /* ]]; then
  WAZUH_LAB_ROOT="${ROOT_DIR}/${WAZUH_LAB_ROOT}"
fi

WAZUH_COMPOSE_DIR="${WAZUH_LAB_ROOT}/wazuh-docker/single-node"
if [[ ! -d "${WAZUH_COMPOSE_DIR}" ]]; then
  echo "Wazuh lab runtime was not found. Run: make wazuh-metasploitable2-lab-up" >&2
  exit 1
fi

AGENT_ID=$(docker compose --env-file "${INTEGRATION_ENV}" -f "${LAB_COMPOSE}" --profile wazuh ps -q wazuh_agent)
if [[ -z "${AGENT_ID}" ]]; then
  echo "The Metasploitable2 Wazuh agent is not running." >&2
  echo "Run: make wazuh-metasploitable2-lab-up" >&2
  exit 1
fi

echo "Metasploitable2 Wazuh sidecar:"
docker compose --env-file "${INTEGRATION_ENV}" -f "${LAB_COMPOSE}" --profile wazuh ps wazuh_agent

echo
echo "Agent processes:"
docker compose --env-file "${INTEGRATION_ENV}" -f "${LAB_COMPOSE}" --profile wazuh \
  exec -T wazuh_agent /var/ossec/bin/wazuh-control status

echo
echo "Agents connected to Wazuh Manager:"
(
  cd "${WAZUH_COMPOSE_DIR}"
  docker compose exec -T wazuh.manager /var/ossec/bin/agent_control -lc
)
