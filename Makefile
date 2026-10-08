.PHONY: up down logs integration-up integration-down integration-logs backend frontend build wazuh-lab-up wazuh-lab-down wazuh-lab-export wazuh-lab-import clean

up:
	docker compose -f deploy/production/docker-compose.yml up --build -d

down:
	docker compose -f deploy/production/docker-compose.yml down

logs:
	docker compose -f deploy/production/docker-compose.yml logs -f

integration-up:
	git submodule sync --recursive
	git submodule update --init --recursive
	docker network inspect backend_net >/dev/null 2>&1 || docker network create backend_net
	docker compose -f deploy/production/docker-compose.yml up --build -d
	docker compose -f integrations/soar-mcp-platform/docker-compose.yml --profile wazuh up --build -d

integration-down:
	docker compose -f integrations/soar-mcp-platform/docker-compose.yml --profile wazuh down
	docker compose -f deploy/production/docker-compose.yml down

integration-logs:
	docker compose -f integrations/soar-mcp-platform/docker-compose.yml --profile wazuh logs -f ingestion_gateway normalizer_worker syslog_receiver wazuh_poller soar_host response_server

backend:
	cd backend && mvn spring-boot:run

frontend:
	cd frontend && npm run dev

build:
	cd backend && mvn clean package -DskipTests
	cd frontend && npm install && npm run build

wazuh-lab-up:
	./scripts/wazuh-lab-up.sh

wazuh-lab-down:
	./scripts/wazuh-lab-down.sh

wazuh-lab-export:
	./scripts/wazuh-export-alerts.sh

wazuh-lab-import:
	./scripts/wazuh-import-alerts.sh

clean:
	rm -rf backend/target frontend/dist frontend/node_modules
