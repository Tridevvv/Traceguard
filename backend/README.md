# Backend

Java 17, Spring Boot 3.5 (Web, Security, Data JPA), JWT + role-based access, ECDSA-signed hash-chained ledger.

## Run locally (no Docker needed)

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

In Windows PowerShell, quote the option: `mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`.

The `dev` profile uses an in-memory H2 database (data is lost on restart) and creates a local-only admin:
`admin@traceguard.local` / `DevAdmin#2026`. It also loads demo data (see below). Swagger UI: http://localhost:8080/swagger-ui.html

To use PostgreSQL instead, run `docker compose up -d` from the repo root, set `TRACEGUARD_JWT_SECRET` (32+ chars),
`TRACEGUARD_ADMIN_EMAIL` and `TRACEGUARD_ADMIN_PASSWORD`, and start with the `postgres` profile.

## Run tests

```bash
mvn test
```

## Configuration

| Env variable | Purpose |
|--------------|---------|
| `TRACEGUARD_JWT_SECRET` | JWT signing secret, at least 32 characters (required outside `dev`) |
| `TRACEGUARD_KEYSTORE_DIR` | Folder for checkpoint private keys (default `./keystore`, git-ignored) |
| `TRACEGUARD_ADMIN_EMAIL`, `TRACEGUARD_ADMIN_PASSWORD` | Creates the first ADMIN at startup if it doesn't exist |

## Packages (`com.traceguard`)

| Package | What it does |
|---------|--------------|
| `auth` | Login, register (ADMIN only), JWT filter, security rules |
| `org` | Organizations and checkpoints (a key pair is created per checkpoint) |
| `catalog` | Products, batches, tag issuing, batch recall |
| `reader` | `Reader` interface and `SimulatedReader` |
| `ledger` | Signed, hash-chained, append-only events; chain check |
| `crypto` | SHA-256, ECDSA signing, key store |
| `audit` | Append-only log of user actions |
| `policy` | Route policies: approved checkpoint order and time limits per product type |
| `verify` | Verification engine (verdict + reasons), route checker, public rate limit |
| `anomaly` | Rule-based detectors: cloned tag, impossible travel, scan burst |
| `alert` | Alerts (deduplicated while open), log and optional SMTP notifications |
| `demo` | Dev-only demo data seeder |

## Verdicts and reasons

`GET /api/verify/{tagId}` (signed in) and `GET /api/public/verify/{tagId}` (no login, 60 requests/minute per client)
return `AUTHENTIC`, `SUSPECT` or `COUNTERFEIT` with machine-readable reasons and a timeline. The verdict is the worst
impact among the reasons.

| Reason code | Verdict | Meaning |
|-------------|---------|---------|
| `HASH_MISMATCH`, `LINK_BROKEN`, `SEQUENCE_GAP`, `SIGNATURE_INVALID` | COUNTERFEIT | Ledger edited, forged or incomplete |
| `UNREGISTERED_TAG` | COUNTERFEIT | Tag ID was never issued |
| `DUPLICATE_PRESENCE` | COUNTERFEIT | Same tag at two distant sites within 60 s (cloned) |
| `IMPOSSIBLE_TRAVEL` | COUNTERFEIT | Speed between checkpoints above 900 km/h |
| `ROUTE_DEVIATION` | SUSPECT | Approved step skipped, or product went backwards |
| `UNAUTHORIZED_LOCATION` | SUSPECT | Scan at a checkpoint not on the route policy |
| `TIMING_VIOLATION` | SUSPECT | Hop or whole journey slower than the policy limit, or timestamps out of order |
| `SCAN_BURST` | SUSPECT | More than 5 scans of one tag within 60 s |
| `BATCH_RECALLED` | SUSPECT | Manufacturer recalled the batch |
| `NO_HISTORY` | SUSPECT | Tag issued but never scanned |
| `NO_ROUTE_POLICY` | (info) | Route and timing were not checked |

Thresholds are configurable under `traceguard.anomaly.*`. Every scan runs a verification straight away, so problems raise
alerts immediately. Alerts are deduplicated while OPEN. HIGH alerts are logged and, if SMTP is configured
(`spring.mail.host`, `spring.mail.port`, ...), emailed to admins and the product's manufacturer users.
A public lookup of an unknown tag ID never raises an alert, so guessing IDs cannot flood the center.

## Demo data (dev profile)

Sign in as `admin@traceguard.local`, or as `manufacturer@`, `distributor@`, `retailer@` or `auditor@demo.local`
(password `DemoUser#2026`). Verify these tags, with or without logging in:

| Tag | Story | Verdict |
|-----|-------|---------|
| `TG-DEMO-GENUINE` | Boston, Albany, Chicago within all limits | AUTHENTIC |
| `TG-DEMO-CLONED` | Boston and Chicago 40 s apart | COUNTERFEIT |
| `TG-DEMO-FAST` | Boston to Albany in 2 minutes | COUNTERFEIT |
| `TG-DEMO-TAMPERED` | Event edited in the database afterwards | COUNTERFEIT |
| `TG-DEMO-DETOUR` | Warehouse step skipped | SUSPECT |
| `TG-DEMO-LATE` | 100 h to reach the warehouse (limit 48 h) | SUSPECT |
| `TG-DEMO-RECALLED` | Batch recalled | SUSPECT |
| `TG-DEMO-NOHISTORY` | Never scanned | SUSPECT |

## Try it

1. Log in as admin: `POST /api/auth/login`
2. `POST /api/organizations`, `POST /api/checkpoints`, `POST /api/auth/register` (manufacturer, distributor)
3. As the manufacturer: `POST /api/products`, `POST /api/products/{id}/batches`, `POST /api/batches/{id}/tags?count=5`
4. `POST /api/scans/simulate` at two checkpoints, then `GET /api/tags/{tagId}/chain-check`

## Notes

- The "no UPDATE/DELETE on events" rule is enforced in code (immutable entity, repository without those methods).
  The PostgreSQL trigger in `database/schema.sql` is not applied yet because Hibernate creates the tables for now.
  Switching to Flyway migrations, which can include the trigger, is still to do.
- `POST /api/scans` currently uses a staff JWT; checkpoint-credential authentication for real readers comes later.
