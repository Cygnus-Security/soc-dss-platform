.PHONY: up down logs integration-up integration-down integration-logs backend frontend build wazuh-lab-up wazuh-lab-down wazuh-lab-export wazuh-lab-import metasploitable2-lab-up metasploitable2-lab-down metasploitable2-lab-logs metasploitable2-lab-probe metasploitable2-lab-probe-blocked metasploitable2-lab-rules metasploitable2-lab-seed clean

INTEGRATION_ENV ?= deploy/production/.env
SOAR_COMPOSE := integrations/soar-mcp-platform/docker-compose.yml
METASPLOITABLE2_LAB_COMPOSE := deploy/metasploitable2-lab/docker-compose.yml
METASPLOITABLE2_SOAR_OVERRIDE := deploy/metasploitable2-lab/soar.override.yml

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

metasploitable2-lab-up:
	@test -f "$(INTEGRATION_ENV)" || (echo "Missing $(INTEGRATION_ENV). Copy deploy/production/.env.example first." >&2; exit 1)
	docker compose --env-file "$(INTEGRATION_ENV)" -f "$(METASPLOITABLE2_LAB_COMPOSE)" up --build -d
	docker compose --env-file "$(INTEGRATION_ENV)" -f "$(SOAR_COMPOSE)" -f "$(METASPLOITABLE2_SOAR_OVERRIDE)" up -d --no-deps --force-recreate response_server

metasploitable2-lab-down:
	@test -f "$(INTEGRATION_ENV)" || (echo "Missing $(INTEGRATION_ENV)." >&2; exit 1)
	docker compose --env-file "$(INTEGRATION_ENV)" -f "$(SOAR_COMPOSE)" up -d --no-deps --force-recreate response_server
	docker compose --env-file "$(INTEGRATION_ENV)" -f "$(METASPLOITABLE2_LAB_COMPOSE)" down
	@echo "Lab stopped. Set SOAR_EXECUTION_MODE=dry-run before approving non-lab actions."

metasploitable2-lab-logs:
	docker compose --env-file "$(INTEGRATION_ENV)" -f "$(METASPLOITABLE2_LAB_COMPOSE)" logs -f victim firewall_executor

metasploitable2-lab-probe:
	@docker exec soc-dss-metasploitable2-client sh -c 'wget -qO- -T 3 http://$${METASPLOITABLE_VICTIM_IP}:80/ >/dev/null' \
		&& echo "PASS: test client can reach Metasploitable2" \
		|| (echo "BLOCKED: test client cannot reach Metasploitable2" >&2; exit 1)

metasploitable2-lab-probe-blocked:
	@if docker exec soc-dss-metasploitable2-client sh -c 'wget -qO- -T 3 http://$${METASPLOITABLE_VICTIM_IP}:80/ >/dev/null'; then \
		echo "FAIL: test client can still reach Metasploitable2" >&2; exit 1; \
	else \
		echo "PASS: SOAR block prevents the test client from reaching Metasploitable2"; \
	fi

metasploitable2-lab-rules:
	docker exec soc-dss-metasploitable2-firewall iptables -S INPUT

metasploitable2-lab-seed:
	./scripts/metasploitable2-lab-seed.sh

clean:
	rm -rf backend/target frontend/dist frontend/node_modules
