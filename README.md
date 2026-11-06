# TraceGuard

[![CI](https://github.com/Tridevvv/Traceguard/actions/workflows/ci.yml/badge.svg)](https://github.com/Tridevvv/Traceguard/actions/workflows/ci.yml)

TraceGuard is a web application that tracks products from manufacturer to consumer using RFID tag scans and decides whether a product is genuine. It is built for manufacturers, distributors, retailers, auditors and consumers in supply chains where counterfeits are dangerous, such as pharmaceuticals, electronics and food. Today, paper records and barcodes are easy to forge and RFID tag IDs can be cloned, so nobody can prove where a product has been. TraceGuard records every handoff as a digitally signed event that is linked to the previous one by a SHA-256 hash, so any later edit is detectable. A verification engine then checks the signatures, the approved route and the timing, and an anomaly detector looks for cloned tags and impossible travel. The result is an `AUTHENTIC`, `SUSPECT` or `COUNTERFEIT` verdict with machine-readable reasons. The design follows NIST SP 800-183 (Networks of Things).

## Team

| Member | GitHub Username | Role |
|---|---|---|
| Tridev Prabhu | [@Tridevvv](https://github.com/Tridevvv) | Backend (Spring Boot API, ledger, verification engine, analytics and reports), frontend skeleton, CI |
| Sanjay Gokulakrishanan | SANJAY_GITHUB_USERNAME | Team member |

## Repository Structure

The layout follows the architecture in our Phase 1 design ([docs/architecture.md](docs/architecture.md)). The React frontend and the Spring Boot API are separate deployable units. The backend is organised by feature, and inside each feature the layers are kept apart: a **controller** handles HTTP only (validation, mapping to response records), a **service** holds the business rules, and a **repository** does data access. NIST SP 800-183 roles map to modules as follows: `reader` is the sensor, `ledger`, `crypto` and the database are the external utility, and `verify` and `anomaly` are the decision trigger.

```
Traceguard/
  backend/                         Java 17 + Spring Boot 3.5 REST API (Maven)
    src/main/java/com/traceguard/
      auth/          JWT login, roles (RBAC), users, security configuration
      org/           organizations and checkpoints (one ECDSA key pair per checkpoint)
      catalog/       products, batches, tag issuing, batch recall
      reader/        Reader interface + SimulatedReader (no RFID hardware needed)
      ledger/        signed, SHA-256 hash-chained, append-only scan events
      crypto/        hashing, ECDSA signatures, key store
      policy/        route policies: approved checkpoint order and time limits
      verify/        verification engine, route checker, public endpoint rate limit
      anomaly/       cloned-tag, impossible-travel and scan-burst detectors
      alert/         alerts center and notifications (log; email when SMTP is configured)
      analytics/     dashboard aggregates and verification history
      report/        CSV and PDF audit and product-history reports
      audit/         append-only log of user actions
      demo/          sample data loaded by the dev profile
      common/        shared types, error handling, configuration
    src/test/java/com/traceguard/    JUnit 5 unit and integration tests
  frontend/                        React 19 + TypeScript (Vite, Material-UI)
    src/api/         fetch client that attaches the JWT
    src/auth/        auth context and role-based route guard
    src/pages/       Landing, Login, Dashboard, Products, ScanConsole, Verify, Admin
    src/*.test.*     Vitest + Testing Library tests
  database/                        PostgreSQL schema draft (schema.sql)
  docs/                            Phase 1 design: requirements, architecture, UML, API, threat model
  .github/workflows/ci.yml         GitHub Actions: builds and tests backend and frontend on every PR
  docker-compose.yml               PostgreSQL + Redis (only for the optional postgres profile)
```

## Setup and Run

**Prerequisites:** Git, JDK 17 or newer, Maven 3.9 or newer, and Node.js 24 (22.22 or newer also works). No accounts, API keys or RFID hardware are needed, because readers are simulated. Docker is optional and only needed for PostgreSQL.

```bash
# Clone
git clone https://github.com/Tridevvv/Traceguard.git
cd Traceguard

# Install dependencies (the backend's are downloaded by Maven on the first run)
(cd frontend && npm install)

# Run the application: two terminals, both started from the Traceguard folder
(cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev)   # terminal 1: API on http://localhost:8080
(cd frontend && npm run dev)                                        # terminal 2: app on http://localhost:5173

# Run tests
(cd backend && mvn test)
(cd frontend && npm test)
```

On Windows PowerShell, put the Maven option in quotes (`mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`), and use `npm.cmd` instead of `npm` if script execution is disabled.

The `dev` profile needs no configuration. It uses an in-memory H2 database, which resets on every restart, and loads demo data:

| Sign in as | Password | Role |
|---|---|---|
| `admin@traceguard.local` | `DevAdmin#2026` | ADMIN |
| `manufacturer@demo.local` | `DemoUser#2026` | MANUFACTURER |
| `distributor@demo.local`, `retailer@demo.local`, `auditor@demo.local` | `DemoUser#2026` | DISTRIBUTOR, RETAILER, AUDITOR |

These passwords exist only in the local dev profile. Outside `dev`, set `TRACEGUARD_JWT_SECRET` (at least 32 characters), `TRACEGUARD_ADMIN_EMAIL` and `TRACEGUARD_ADMIN_PASSWORD`. To use PostgreSQL, copy `.env.example` to `.env`, run `docker compose up -d`, and start the backend with the `postgres` profile. No real secrets are committed. API documentation (Swagger UI) is at http://localhost:8080/swagger-ui.html. More detail is in [backend/README.md](backend/README.md) and [frontend/README.md](frontend/README.md).

## Progress So Far

| Functional Requirement (from Phase 1) | Status | Notes |
|---|---|---|
| FR-1: Registration and login with JWT and role-based access | Complete | 5 staff roles; consumers need no account; bcrypt passwords |
| FR-2: Create organizations, checkpoints and route policies | Partial | All three work in the API; the UI covers organizations and checkpoints, and route policies are set through Swagger for now |
| FR-3: Register products and batches; generate tag IDs and QR codes | Partial | Products, batches and unique tag IDs work in UI and API; printing QR labels is not built yet |
| FR-4: Accept scans and create signed, hash-linked events | Complete | Simulated reader behind a `Reader` interface; ECDSA (P-256) signature per checkpoint |
| FR-5: Append-only ledger with no update or delete | Complete | No update/delete in the API or repository, and the entity is immutable; the matching PostgreSQL trigger is drafted in `database/schema.sql` |
| FR-6: Verify signatures, hash chain, route order and timing | Complete | `GET /api/verify/{tagId}`; the UI Verify page still shows only the chain check |
| FR-7: Detect anomalies | Complete | Cloned tag, impossible travel, unregistered tag, unauthorized location, scan burst; thresholds configurable |
| FR-8: AUTHENTIC / SUSPECT / COUNTERFEIT verdict with reasons | Complete | 14 reason codes, each with a message; shown in the API, UI display is Phase 3 |
| FR-9: Raise, display and manage alerts; email on high severity | Partial | Alerts are raised, deduplicated, scoped by role and can be resolved through the API; email works when SMTP is configured; alerts center UI not built yet |
| FR-10: Flag or recall all products in a batch | Complete | Recall button in the Product Registry; recalled tags verify as SUSPECT |
| FR-11: Public consumer verification by tag ID or QR code | Partial | Public, rate-limited `GET /api/public/verify/{tagId}` works without login; public page and QR scanning not built yet |
| FR-12: Analytics (scan volume, verdicts, alerts, checkpoints) | Partial | Five `/api/analytics/*` endpoints, scoped by role; dashboard charts not built yet |
| FR-13: Export audit reports as PDF/CSV | Partial | `/api/reports/audit` (user activity, alerts, ledger) and `/api/reports/tags/{tagId}` produce CSV and PDF; download buttons in the UI not built yet |
| FR-14: Log all user actions for audit | Complete | Append-only audit log; `GET /api/audit-log` for admins and auditors |

**Working end to end:** with the backend and frontend running as described above:

1. Sign in as `manufacturer@demo.local`. Under **Product Registry**, create a product, open its batches, add a batch and issue tags. Copy one tag ID.
2. Under **Scan Console**, scan the tag at **Factory** (REGISTERED), then at **Warehouse** (RECEIVED). Each scan becomes a signed event, and the timeline shows each event's hash linked to the previous one.
3. Under **Verify a Tag**, check the tag. It shows **CHAIN INTACT**, with the full timeline.
4. Open http://localhost:8080/api/public/verify/TG-DEMO-CLONED with no login. It returns `COUNTERFEIT` with the reason `DUPLICATE_PRESENCE`: the same tag was seen in Boston and Chicago 40 seconds apart. The other demo tags show the remaining verdicts: `TG-DEMO-GENUINE`, `TG-DEMO-FAST`, `TG-DEMO-TAMPERED`, `TG-DEMO-DETOUR`, `TG-DEMO-LATE`, `TG-DEMO-RECALLED` and `TG-DEMO-NOHISTORY`.

If you scan one tag at two far-apart checkpoints within a minute, the engine correctly flags it as cloned. That is the anomaly detector working as intended.

## Testing So Far

- **Backend:** JUnit 5 with Spring Boot Test and MockMvc. **69 tests in 9 classes, all passing.**
  - **Ledger:** linking, tamper detection, forged signatures, replay rejection.
  - **Route checker and anomaly detectors:** pure unit tests.
  - **Verification engine:** every verdict scenario.
  - **Analytics and report exporters:** including CSV formula-injection protection and valid PDF output.
  - **REST API and roles:** 401 and 403 rules, manufacturer data isolation, the public endpoint rate limit, and a full supply-chain flow.
- **Frontend:** Vitest with React Testing Library. **8 tests, all passing:** JWT decoding, role-based routing and menus, and the login form.
- **CI:** GitHub Actions runs both suites on every pull request and on every push to `main`.

## Next Steps (Phase 3)

- Frontend for the Phase 3 backend:
  - verdict view with reasons
  - alerts center
  - analytics dashboard charts
  - report download buttons
  - route-policy editor
- Public consumer verification page with QR codes: printable tag labels and camera scanning.
- Database migrations with Flyway, including the PostgreSQL trigger that blocks edits to the ledger.
- Checkpoint-credential authentication for real readers.
- UI polish.
- Phase 4 testing: load tests (target 100 scans/s, verification under 2 s) and the security scenarios from the threat model.

## Team Contributions

| Member | Contributions This Phase |
|---|---|
| Tridev Prabhu | **Design:** Phase 1 documents (requirements, architecture with NIST mapping, UML, database schema, API design, threat model).<br>**Backend:** project setup; JWT authentication and RBAC (`auth`); organizations and checkpoints (`org`); products, batches, tags and recall (`catalog`); signed, hash-chained ledger and key store (`ledger`, `crypto`); simulated reader (`reader`); route policies, verification engine and anomaly detectors (`policy`, `verify`, `anomaly`); alerts and notifications (`alert`); analytics and CSV/PDF reports (`analytics`, `report`); audit log (`audit`); demo data seeder.<br>**Frontend:** skeleton (login, role-based menu and routes, Product Registry, Scan Console, Verify, Admin pages).<br>**Infrastructure and tests:** GitHub Actions CI; all 69 backend and 8 frontend tests. |
| Sanjay Gokulakrishanan | No code contributions in this phase. |
