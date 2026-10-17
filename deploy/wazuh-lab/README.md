# Wazuh Lab

This lab installs an official Wazuh single-node Docker stack for validation and log collection. It is separate from `deploy/production/docker-compose.yml` because SOC DSS is the product under test, while Wazuh is the alert source used for experiments.

The lab uses the upstream `wazuh/wazuh-docker` repository, pinned by `WAZUH_VERSION`, and stores the cloned runtime under `deploy/wazuh-lab/runtime/`.

## Requirements

- Docker Engine or Docker Desktop
- Docker Compose plugin
- Git
- At least 4 CPU cores, 8 GB RAM, and 50 GB free disk space for the Wazuh single-node stack

On Linux, Wazuh Indexer also requires:

```bash
sudo sysctl -w vm.max_map_count=262144
```

## Start Wazuh

```bash
cp deploy/wazuh-lab/.env.example deploy/wazuh-lab/.env
make wazuh-lab-up
```

The script clones the official Wazuh Docker repo, generates self-signed certificates if needed, and starts the single-node stack.

Wazuh dashboard:

```text
https://localhost
```

Default lab credentials:

```text
Username: admin
Password: SecretPassword
```

## Connect the Metasploitable2 victim

Metasploitable2 is based on an obsolete Ubuntu release, so the current Wazuh
agent package is not installed inside that container. The lab instead runs the
official Wazuh agent image as a sidecar named
`soc-dss-metasploitable2-wazuh-agent`. It mounts the victim's `/var/log`
read-only and forwards its SSH, system, Apache, FTP, and Samba logs to Wazuh.

The Wazuh-only workflow can run with the checked-in safe defaults. To override
the manager host, agent name, or ports, create the integration environment:

```bash
cp deploy/production/.env.example deploy/production/.env
```

Then start Wazuh, the Metasploitable2 victim, and its agent together:

```bash
make wazuh-metasploitable2-lab-up
```

This target does not start the SOAR firewall executor and therefore does not
require a webhook token. Run `make metasploitable2-lab-up` separately when the
SOAR response path is also needed.

The default connection uses the manager ports published on the Docker host:

```dotenv
WAZUH_MANAGER_HOST=host.docker.internal
WAZUH_MANAGER_PORT=1514
WAZUH_REGISTRATION_PORT=1515
WAZUH_AGENT_NAME=soc-dss-metasploitable2-victim
```

On a remote Ubuntu Wazuh host, replace `WAZUH_MANAGER_HOST` with that host's IP
or resolvable DNS name and allow inbound TCP 1514 and 1515.

Check both the sidecar processes and the manager-side connection list:

```bash
make wazuh-metasploitable2-agent-status
```

Follow agent enrollment and forwarding logs:

```bash
make wazuh-metasploitable2-agent-logs
```

In the Wazuh dashboard, open **Agents management > Summary** and look for
`soc-dss-metasploitable2-victim`.

The sidecar provides real application and authentication log collection. Its
OS inventory, process inventory, rootcheck, and file-integrity state describe
the sidecar rather than the legacy victim and are intentionally disabled in
the lab configuration. Use a supported Ubuntu victim if those endpoint-level
capabilities are required.

## Generate Alerts

Use the Wazuh dashboard or an enrolled Wazuh agent to generate test alerts. The manager alert file used by SOC DSS is:

```text
/var/ossec/logs/alerts/alerts.json
```

## Export Alerts

```bash
make wazuh-lab-export
```

Default output:

```text
data/results/wazuh-alerts.json
```

You can choose another output file:

```bash
./scripts/wazuh-export-alerts.sh data/results/experiment-01-alerts.json
```

## Import Into SOC DSS

Start the SOC DSS product first:

```bash
make up
```

Then export Wazuh alerts and import them into SOC DSS:

```bash
make wazuh-lab-import
```

This calls:

```text
POST /api/v1/import/wazuh-alerts
POST /api/v1/incidents/correlate
```

## Stop Wazuh

```bash
make wazuh-lab-down
```

To remove Wazuh volumes completely, run from the generated runtime directory:

```bash
cd deploy/wazuh-lab/runtime/wazuh-docker/single-node
docker compose down -v
```
