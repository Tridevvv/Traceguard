# API design (draft v0.1)

Base path `/api`. JSON over HTTPS. Auth: `Authorization: Bearer <JWT>` unless marked public. The final contract will be generated with OpenAPI/Swagger.

## Auth

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| POST | `/auth/login` | public | Email + password, returns JWT |
| POST | `/auth/register` | ADMIN | Create a user (FR-1) |

## Organizations and checkpoints (FR-2)

| Method | Path | Access |
|--------|------|--------|
| GET/POST | `/organizations` | ADMIN |
| GET/POST | `/checkpoints` | ADMIN (GET: any staff role) |
| GET | `/route-policies` | any signed-in role |
| POST | `/route-policies` | ADMIN (one policy per product type; steps are the approved checkpoint order) |
| PUT | `/route-policies/{id}` | ADMIN (replace steps and limits) |

## Catalog (FR-3, FR-10)

| Method | Path | Access |
|--------|------|--------|
| GET/POST | `/products` | MANUFACTURER, ADMIN |
| POST | `/products/{id}/batches` | MANUFACTURER |
| POST | `/batches/{id}/tags?count=N` | MANUFACTURER (issues tag IDs and QR codes) |
| POST | `/batches/{id}/recall` | MANUFACTURER, ADMIN |

## Scans and ledger (FR-4, FR-5)

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| POST | `/scans` | checkpoint credentials | Reader gateway: `{tagId, nonce, timestamp}` creates a signed event |
| GET | `/tags/{tagId}/events` | staff, AUDITOR | Ledger entries for a tag |

There is intentionally no PUT or DELETE for events.

## Verification (FR-6, FR-7, FR-8, FR-11)

| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| GET | `/verify/{tagId}` | any signed-in role | Full verdict, reasons, timeline; raises alerts |
| GET | `/public/verify/{tagId}` | public | Same verdict for consumers; 60 requests/min per client (HTTP 429 above that); never raises alerts for unknown tag IDs |

Verdict response:

```json
{
  "tagId": "TG-000123",
  "verdict": "SUSPECT",
  "reasons": [
    { "code": "IMPOSSIBLE_TRAVEL", "message": "Boston to Chicago in 10 minutes", "eventSeq": 3 }
  ],
  "timeline": [
    { "seq": 1, "type": "REGISTERED", "checkpoint": "Factory", "occurredAt": "2026-11-02T09:00:00Z" }
  ]
}
```

## Alerts and analytics (FR-9, FR-12)

| Method | Path | Access |
|--------|------|--------|
| GET | `/alerts?status=OPEN` | ADMIN, AUDITOR (all); MANUFACTURER (own products only) |
| PATCH | `/alerts/{id}` body `{"status":"ACKNOWLEDGED"\|"RESOLVED"}` | ADMIN, MANUFACTURER (own) |
| GET (not built yet) | `/analytics/scan-volume`, `/analytics/verdicts`, `/analytics/alerts`, `/analytics/checkpoints` | staff, AUDITOR |

## Reports and audit (FR-13, FR-14)

| Method | Path | Access |
|--------|------|--------|
| GET (not built yet) | `/reports/audit?format=pdf\|csv&from=&to=` | ADMIN, AUDITOR |
| GET | `/audit-log` | ADMIN, AUDITOR |

## Error format

```json
{ "status": 403, "error": "FORBIDDEN", "message": "Role RETAILER cannot create products" }
```
