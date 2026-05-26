.PHONY: up down logs backend frontend build clean

up:
	docker compose -f deploy/production/docker-compose.yml up --build -d

down:
	docker compose -f deploy/production/docker-compose.yml down

logs:
	docker compose -f deploy/production/docker-compose.yml logs -f

backend:
	cd backend && mvn spring-boot:run

frontend:
	cd frontend && npm run dev

build:
	cd backend && mvn clean package -DskipTests
	cd frontend && npm install && npm run build

clean:
	rm -rf backend/target frontend/dist frontend/node_modules
