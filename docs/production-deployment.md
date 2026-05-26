# Production Deployment

## Start Services

```bash
cp deploy/production/.env.example deploy/production/.env
docker compose -f deploy/production/docker-compose.yml up --build -d
```

## Access

```text
Dashboard: http://localhost:8080
API:       http://localhost:8080/api/v1
```

## Services

- `postgres`: PostgreSQL database
- `backend`: Spring Boot API and DSS engine
- `frontend`: React static frontend served by Nginx

## Stop Services

```bash
docker compose -f deploy/production/docker-compose.yml down
```

## Reset Database

```bash
docker compose -f deploy/production/docker-compose.yml down -v
```
