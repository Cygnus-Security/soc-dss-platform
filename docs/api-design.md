# API Design

Base path:

```text
/api/v1
```

## Health

```http
GET /api/v1/health
```

## Import Wazuh Alerts

```http
POST /api/v1/import/wazuh-alerts
Content-Type: multipart/form-data
```

Field:

```text
file=<alerts.json>
```

## Alerts

```http
GET /api/v1/alerts
```

## Incidents

```http
GET /api/v1/incidents
GET /api/v1/incidents/{id}
POST /api/v1/incidents/correlate
```

## SOAR Response Actions

```http
GET  /api/v1/response-actions
GET  /api/v1/response-actions/summary
POST /api/v1/response-actions/bulk-dispatch
```

Bulk dispatch creates, approves, signs, and enqueues up to 100 temporary IP
block actions. The backend resolves each target from the incident `sourceIp`;
the client cannot supply an arbitrary target. Duplicate incident IDs are
removed while preserving request order, and a failure for one incident does
not roll back the rest of the batch.

```json
{
  "incidentIds": [6473, 36629],
  "actionType": "temporary_ip_block",
  "rationale": "Contain selected attack sources"
}
```

The response contains per-incident `DISPATCHED`, `SKIPPED`, or `FAILED`
outcomes. Requests require the authenticated session and CSRF header used by
the web UI.

## Dashboard

```http
GET /api/v1/dashboard/summary
```

## Reports

```http
GET /api/v1/reports/incidents.csv
```
