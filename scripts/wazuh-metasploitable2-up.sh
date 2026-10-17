#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
INTEGRATION_ENV=${INTEGRATION_ENV:-deploy/production/.env}
LAB_COMPOSE="${ROOT_DIR}/deploy/metasploitable2-lab/docker-compose.yml"

if [[ "${INTEGRATION_ENV}" != /* ]]; then
  INTEGRATION_ENV="${ROOT_DIR}/${INTEGRATION_ENV}"
fi
if [[ ! -f "${INTEGRATION_ENV}" ]]; then
  INTEGRATION_ENV="${ROOT_DIR}/deploy/production/.env.example"
  echo "deploy/production/.env was not found; using safe Wazuh lab defaults."
fi

# Compose validates the firewall executor's required token even though this
# command starts only the victim and Wazuh agent. This placeholder is never
# passed to a running firewall service in this profile-only workflow.
export SOAR_ACTION_WEBHOOK_TOKEN=${SOAR_ACTION_WEBHOOK_TOKEN:-wazuh-profile-unused}

"${ROOT_DIR}/scripts/wazuh-lab-up.sh"

docker compose --env-file "${INTEGRATION_ENV}" -f "${LAB_COMPOSE}" --profile wazuh \
  up -d victim wazuh_agent

echo "Wazuh and the Metasploitable2 log agent are starting."
echo "Check enrollment: make wazuh-metasploitable2-agent-status"
