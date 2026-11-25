# TraceGuard

[![CI](https://github.com/Tridevvv/Traceguard/actions/workflows/ci.yml/badge.svg)](https://github.com/Tridevvv/Traceguard/actions/workflows/ci.yml)

TraceGuard is a web application that tracks products from manufacturer to consumer using RFID tag scans and decides whether a product is genuine. It is built for manufacturers, distributors, retailers, auditors and consumers in supply chains where counterfeits are dangerous, such as pharmaceuticals, electronics and food. Today, paper records and barcodes are easy to forge and RFID tag IDs can be cloned, so nobody can prove where a product has been. TraceGuard records every handoff as a digitally signed event that is linked to the previous one by a SHA-256 hash, so any later edit is detectable. A verification engine then checks the signatures, the approved route and the timing, and anomaly rules look for cloned tags and impossible travel. The result is an `AUTHENTIC`, `SUSPECT` or `COUNTERFEIT` verdict with machine-readable reasons. The design follows NIST SP 800-183 (Networks of Things).

## Team

| Member | GitHub Username | Role |
|---|---|---|
| Tridev Prabhu | [@Tridevvv](https://github.com/Tridevvv) | Backend (Spring Boot API, ledger, verification engine, analytics and reports), admin and scan UI, CI and testing |
| Sanjay Gokulakrishanan | [@sanjusanjay23](https://github.com/sanjusanjay23) | Frontend (verification page, alerts center, analytics dashboard, consumer page and QR labels, report downloads), Phase 1 SRS document |

## Repository Structure

The layout follows the architecture in our Phase 1 design ([docs/architecture.md](docs/architecture.md)). The React frontend and the Spring Boot API are separate deployable units. The backend is organized by feature, and inside each feature the layers are kept apart: a **controller** handles HTTP only (validation, mapping to response records), a **service** holds the business rules, and a **repository** does data access. NIST SP 800-183 roles map to modules as follows: `reader` is the sensor, `ledger`, `crypto` and the database are the external utility, and `verify` and `anomaly` are the decision trigger.

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
      verify/        verification engine: VerificationCheck rules, VerdictIssued event, public rate limit
      anomaly/       MovementRule (cloned tag, impossible travel) and ScanBurstRule
      alert/         alerts center, VerdictAlertListener, notifiers (log; email when SMTP is configured)
      analytics/     dashboard aggregates and verification history
      report/        CSV and PDF audit and product-history reports
      audit/         append-only log of user actions
      demo/          sample data loaded by the dev profile
      common/        shared types, error handling, configuration
    src/test/java/com/traceguard/    JUnit 5 unit and integration tests
  frontend/                        React 19 + TypeScript (Vite, Material-UI)
    src/api/         fetch client that attaches the JWT
    src/auth/        auth context and role-based route guard
    src/api/reports  URL builders for the CSV/PDF report endpoints
    src/qr.ts        QR label links and scanned-value parsing
    src/pages/       Landing, Login, Dashboard, Products, ScanConsole, Verify, VerdictPanel,
                     Admin, RoutePolicyEditor, EventTimeline, Alerts, Analytics, Charts,
                     Reports, PublicCheck (consumer page), QrLabels
    src/test/        test setup and shared helpers (fake backend, sign-in)
    src/**/*.test.*  Vitest + Testing Library tests
  database/                        PostgreSQL schema draft (schema.sql)
  docs/                            Phase 1 design: requirements, architecture, UML, API, threat model
  .github/workflows/ci.yml         GitHub Actions: tests with coverage, lint and build on every PR
  docker-compose.yml               PostgreSQL + Redis (only for the optional postgres profile)
```

## Setup and Run

**Prerequisites:** Git, JDK 17 or newer, Maven 3.9 or newer, and Node.js 24.15 or newer in the 24.x series (22.22.2 or newer in the 22.x series also works). No accounts, API keys or RFID hardware are needed, because readers are simulated. Docker is optional and only needed for PostgreSQL.

```bash
# Clone
git clone https://github.com/Tridevvv/Traceguard.git
cd Traceguard

# Install dependencies (the backend's are downloaded by Maven on the first run)
(cd frontend && npm ci)

# Run the application: two terminals, both started from the Traceguard folder
(cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev)   # terminal 1: API on http://localhost:8080
(cd frontend && npm run dev)                                        # terminal 2: app on http://localhost:5173
```

On Windows PowerShell, put the Maven option in quotes (`mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`), and use `npm.cmd` instead of `npm` if script execution is disabled. Test commands are in [Testing](#testing).

The `dev` profile needs no configuration. It uses an in-memory H2 database, which resets on every restart, and loads demo data:

| Sign in as | Password | Role |
|---|---|---|
| `admin@traceguard.local` | `DevAdmin#2026` | ADMIN |
| `manufacturer@demo.local` | `DemoUser#2026` | MANUFACTURER |
| `distributor@demo.local`, `retailer@demo.local`, `auditor@demo.local` | `DemoUser#2026` | DISTRIBUTOR, RETAILER, AUDITOR |

The demo data includes one tag per verdict scenario: `TG-DEMO-GENUINE`, `TG-DEMO-CLONED`, `TG-DEMO-FAST`, `TG-DEMO-TAMPERED`, `TG-DEMO-DETOUR`, `TG-DEMO-LATE`, `TG-DEMO-RECALLED` and `TG-DEMO-NOHISTORY`.

These passwords exist only in the local dev profile. Outside `dev`, set `TRACEGUARD_JWT_SECRET` (at least 32 characters), `TRACEGUARD_ADMIN_EMAIL` and `TRACEGUARD_ADMIN_PASSWORD`. To use PostgreSQL, copy `.env.example` to `.env`, run `docker compose up -d`, and start the backend with the `postgres` profile. No real secrets are committed. API documentation (Swagger UI) is at http://localhost:8080/swagger-ui.html. More detail is in [backend/README.md](backend/README.md) and [frontend/README.md](frontend/README.md).

**Primary use case end to end:**

1. Sign in as `admin@traceguard.local`. Under **Admin Panel → Route policies**, confirm the demo policy for `pharma` lists Factory, Warehouse and Pharmacy in that order. The `dev` profile creates this policy at startup.
2. Sign in as `manufacturer@demo.local`. Under **Product Registry**, create a product of type `pharma`, add a batch and issue two tags. Copy both tag IDs.
3. Under **Scan Console**, record the first tag at Factory (`REGISTERED`, 30 hours ago), Warehouse (`RECEIVED`, 24 hours ago) and Pharmacy (`DELIVERED`, 2 hours ago). Set **Scan time** on each event to simulate realistic travel. Each scan becomes a signed event linked to the previous one.
4. Under **Verify a Tag**, check the tag. It shows **AUTHENTIC**, with the ledger timeline and hashes.
5. Scan the second tag at Factory (`REGISTERED`, 30 hours ago) and then directly at Pharmacy (`DELIVERED`, 2 hours ago). Verify shows **SUSPECT** with `ROUTE_DEVIATION`.
6. Verify `TG-DEMO-CLONED`. It shows **COUNTERFEIT** with `DUPLICATE_PRESENCE` because the same tag was seen in Boston and Chicago 40 seconds apart. As admin or manufacturer, open **Alerts** to acknowledge and resolve the finding, then open **Analytics** to view verdicts, scan volume and alerts by reason. Use **Reports** to download a product's history as CSV or PDF. Sign in as admin or auditor to download the audit log.
7. As a consumer, with no login, open http://localhost:5173/check/TG-DEMO-CLONED (or type the tag on the home page) to see the same verdict and the product's journey. In the Product Registry, **QR labels** on a batch prints one QR code per tag. For a phone demo, start the frontend with `npm run dev -- --host`, open the app using the computer's LAN address on the same network, and generate labels from that address so the phone can reach the links.

## Feature Status

| Functional Requirement (from Phase 1) | Status | Where to Find It |
|---|---|---|
| FR-1: Registration and login with JWT and role-based access | Complete | `auth/` (`SecurityConfig`, `JwtService`, `UserService`); `frontend/src/auth/` |
| FR-2: Create organizations, checkpoints and route policies | Complete | `org/OrgService`, `policy/RoutePolicyService`; `pages/Admin.tsx`, `pages/RoutePolicyEditor.tsx` |
| FR-3: Register products and batches; generate unique tag IDs and QR codes | Complete | `catalog/CatalogService`, `pages/Products.tsx`; printable QR labels per batch in `pages/QrLabels.tsx` (links built by `src/qr.ts`) |
| FR-4: Accept scans and create signed, hash-linked events | Complete | `ledger/LedgerService.append()`, `reader/SimulatedReader`, `crypto/SignatureService`; `pages/ScanConsole.tsx` |
| FR-5: Append-only ledger with no update or delete | Complete | `ledger/ScanEvent` (`@Immutable`), `ScanEventRepository` (no update or delete methods) |
| FR-6: Verify signatures, hash chain, route order and timing | Complete | `verify/VerificationService`, `ChainIntegrityCheck`, `RouteChecker`; `pages/Verify.tsx` |
| FR-7: Detect anomalies (cloned tag, impossible travel, unregistered tag, unauthorized location, scan burst) | Complete | `anomaly/MovementRule`, `anomaly/ScanBurstRule`, `verify/RouteChecker`; thresholds in `application.yml` |
| FR-8: AUTHENTIC / SUSPECT / COUNTERFEIT verdict with machine-readable reasons | Complete | `common/Verdicts` (14 reason codes); `pages/VerdictPanel.tsx` |
| FR-9: Raise, display and manage alerts; email on high severity | Complete | `alert/AlertService`, `VerdictAlertListener`, `SmtpNotifier`; alerts center with status filter, acknowledge and resolve in `pages/Alerts.tsx` |
| FR-10: Flag or recall all products in a batch | Complete | `CatalogService.recall()`, `verify/RecallCheck`; Recall button in `pages/Products.tsx` |
| FR-11: Public consumer verification by tag ID or QR code | Complete | `GET /api/public/verify/{tagId}` (`VerifyController`, `PublicRateLimitFilter`); consumer page `/check/:tagId` in `pages/PublicCheck.tsx`, reached from the home page or a QR label |
| FR-12: Analytics (scan volume, verdicts, alerts, checkpoints) | Complete | `analytics/AnalyticsService`, `/api/analytics/*`; dashboard in `pages/Analytics.tsx` with charts in `pages/Charts.tsx` |
| FR-13: Export audit reports as PDF/CSV | Complete | `report/ReportService`, `CsvExporter`, `PdfExporter`, `/api/reports/*`; `pages/Reports.tsx` and history downloads on `pages/Verify.tsx`, via `download()` in `api/client.ts` |
| FR-14: Log all user actions for audit | Complete | `audit/AuditService`, `GET /api/audit-log` |

## Scope and Design Changes Since Phase 1

| Change | Reason |
|---|---|
| The verification engine became a set of pluggable `VerificationCheck` rules, and alerts and verification history became listeners of a `VerdictIssued` event. | The Phase 1 design had one verification service that did everything. As the number of checks grew to six, that class needed edits for every new rule and depended on the alert and analytics modules. Splitting it keeps each rule small and testable on its own, and removes the coupling. |
| The Redis cache from the architecture diagram is not used. The public endpoint's rate limit is an in-memory fixed window (60 requests per minute per IP). | Verification of a tag reads only that tag's events and runs well under the 2-second target without a cache. A single-instance demo does not need a shared rate-limit store. Redis stays in `docker-compose.yml` in case we deploy more than one instance. |
| The `dev` profile and the tests use an in-memory H2 database. PostgreSQL is an optional `postgres` profile. | Graders and teammates can run the project with only Java, Maven and Node, with no Docker or database setup. The JPA mappings are the same for both databases. |
| The database schema is created by JPA. Flyway migrations and the PostgreSQL trigger that blocks ledger edits are moved to Phase 5. | The append-only rule is already enforced in code (immutable entity, a repository without update or delete), and the hash chain detects any edit made directly in the database. That last case is covered by a test. Migrations matter once there is a deployed database to upgrade. |
| Anomaly thresholds (maximum speed, duplicate window and distance, burst size) are configuration properties instead of constants. | Real speeds differ by product and region, and the demo data needs tight windows. Configuration lets each deployment tune the rules without code changes. |
| A `NO_ROUTE_POLICY` reason was added as information only. | Without a route policy, route and timing cannot be checked. Reporting this without changing the verdict tells the user why those checks were skipped. |
| Analytics charts are drawn with Material-UI boxes (`pages/Charts.tsx`) instead of Chart.js or Recharts. | The dashboard needs four simple charts: bars, daily columns and one stacked bar. A charting library would add well over 100 kB to a bundle that is already above Vite's size warning, and our own components label every bar with its number, so values are readable without hovering and easy to test. |
| QR codes encode a link to the public check page, and in-page scanning uses the browser's built-in `BarcodeDetector` instead of the `html5-qrcode` library. | A link means any phone camera opens the verdict directly, with no app. The built-in detector covers in-page scanning in Chrome, Edge and Android without another dependency; in other browsers the page asks the user to use the phone camera or type the tag ID. `qrcode.react` is still used to draw the labels. |
| The consumer page is a separate route (`/check`) rather than a mode of the staff Verify page. | Consumers have no account. The public page shows the verdict and the product's journey but not ledger hashes or staff actions, and it explains the rate limit in plain words. The home page verify box now goes to this page. |
| The Scan Console accepts an optional scan time. | Demos and tests need journeys that span hours or days. Both scan endpoints already accept a reader-reported time, because a real reader may upload scans after a delay. A backdated or out-of-order time cannot hide tampering: the time is part of the signed hash, and the route checker flags out-of-order times as `TIMING_VIOLATION`. |

## Design Patterns and Principles Applied

| Pattern / Principle | Where | Why |
|---|---|---|
| Strategy | `verify/VerificationCheck` with `RecallCheck`, `HistoryCheck`, `ChainIntegrityCheck`, `RouteChecker`, `anomaly/MovementRule`, `ScanBurstRule` | Each rule is a separate class. `VerificationService` runs every registered check in `@Order` order and takes the worst impact, so a new rule is a new class with no change to the service (Open/Closed). |
| Strategy | `alert/Notifier` with `LogNotifier` and `SmtpNotifier` | High-severity alerts go to every configured channel. Email is switched on by configuration (`spring.mail.host`), not by code. |
| Observer | `verify/VerdictIssued`, observed by `alert/VerdictAlertListener` and `analytics/VerificationHistoryRecorder` | The verification engine announces a verdict without knowing who reacts. Alerts and history can change, or new listeners can be added, without touching `verify`. |
| Adapter | `reader/Reader` interface with `SimulatedReader` | The ledger depends on our own `ScanReading` type, not on any vendor's reader format. A real RFID reader adapter can replace the simulator later. |
| Repository | Spring Data repositories in every feature (`ScanEventRepository`, `AlertRepository`, `CatalogRepositories`, and others) | Data access stays in one layer. Services contain only business rules, and the same code runs on H2 and PostgreSQL. |
| Layered architecture, Single Responsibility | Every feature: controller (HTTP only) → service (rules) → repository (data) | Controllers stay thin, each class has one reason to change, and business rules can be tested without HTTP. |
| Dependency Injection | Constructor injection throughout, for example `VerificationService(List<VerificationCheck> checks, ...)` | Collaborators are replaceable. Unit tests build `new RouteChecker()` and `new MovementRule(props)` directly, with no Spring context. |
| Immutability | `ScanEvent` (`@Immutable`), Java records for every request, response and `Verdict` | Ledger events can never be changed through the application, and value objects can't be modified after they are created. |
| Separation of data and format | `report/ReportTable`, written by `CsvExporter` and `PdfExporter` | Each report is built once as a table. Adding a format means writing one exporter, not changing every report. |
| Single source of truth, DRY | `frontend/src/nav.ts` drives the menu, the dashboard cards and the route guards; the `useAction` hook handles busy and error state for every form | Role permissions in the UI can't drift apart, and forms don't repeat the same error handling. |
| Container / presentational components | `pages/Analytics.tsx` fetches and shapes the data; `pages/Charts.tsx` (`StatCard`, `BarList`, `DailyColumns`, `StackedBar`) only draws what it is given | Charts are reusable and know nothing about the API, so a new metric is a few lines on the page, and chart behavior is tested through plain props. |
| Facade | `download()` in `frontend/src/api/client.ts`, used by `pages/Reports.tsx` and `pages/Verify.tsx`; report URLs built in `api/reports.ts` | Saving a report needs the JWT header, the file name from `Content-Disposition`, an object URL and error mapping. Pages call one function and never repeat those steps. |

## Testing

- **Unit tests:** 25 pure JUnit 5 tests with no Spring context or database:
  - the route checker and anomaly rules
  - distance calculation
  - CSV/PDF exporters, including CSV formula-injection protection

  26 service tests run against an in-memory database: the ledger (linking, tampering, forged signatures, replay), every verdict scenario of the verification engine, and the analytics aggregates. 49 frontend tests use Vitest and React Testing Library. Each page test renders the whole app with routing and auth against a fake backend (`src/test/helpers.tsx`):
  - JWT decoding, role-based routing and menus
  - the login form
  - the Verify page verdict view and its history downloads
  - the route-policy editor
  - the Scan Console
  - the alerts center: ordering, status filter, resolve, auditor read-only, retailer blocked
  - the analytics dashboard: summary, every chart, period change, manufacturer scope
  - the consumer page: verdict without login, home-page entry, pasted QR links, rate-limit message
  - QR labels: one code per tag, printing, empty batch, link from the Product Registry
  - reports: URL building, file names, CSV/PDF downloads with the JWT, server errors, role-based sections
- **Integration tests:** 22 MockMvc tests drive the full stack through the REST API:
  - the complete supply-chain flow
  - a journey checked against a route policy, ending in AUTHENTIC and SUSPECT verdicts
  - tampering detected, alerted and resolved in the alert center
  - a recall seen on the public endpoint
  - a cloned tag raising an alert visible only to the owning manufacturer
  - role rules (401 and 403)
  - report exports
  - the public rate limit
- **Coverage:** backend 86% of lines overall (JaCoCo), with 100% on the verification engine, 98% on anomaly detection and 91% on the ledger, which meets NFR-6. Frontend 77% of lines (Vitest v8).
- **CI:** all suites run in GitHub Actions on every pull request and every push to `main`, and the backend coverage report is uploaded as a build artifact.

```bash
(cd backend && mvn verify)               # 73 backend tests; coverage report in backend/target/site/jacoco/index.html
(cd frontend && npm test)                # frontend tests
(cd frontend && npm run test:coverage)   # frontend tests with coverage
(cd frontend && npm run lint)            # oxlint
```

## Known Issues

- In-page QR scanning on the consumer page works only in browsers with the built-in `BarcodeDetector` (Chrome, Edge, Android). Elsewhere, users scan the label with the phone camera, which opens the same page.
- Camera scanning is not covered by automated tests, because jsdom has no camera. The QR link format it relies on is unit-tested in `src/qr.test.ts`.
- The H2 database in the `dev` profile is in memory, so data created during a session is lost when the backend restarts. The demo data is loaded again on every start.
- Any signed-in staff user can simulate a scan at any checkpoint. Authenticating real readers with checkpoint credentials is planned for the QA phase.
- The public endpoint's rate limit is held in memory, so it applies per server instance.
- The performance targets (verification under 2 seconds, 100 scans per second) have not been load-tested yet. This is planned for the QA phase.
- TLS is not configured locally. It comes with the deployment setup (Nginx in front of the app) in Phase 5.
- The frontend production bundle (about 620 kB) is over Vite's 500 kB warning size. Code-splitting the pages is planned.

## Team Contributions (Phase 3)

| Member | Contributions This Phase | Commits (total) |
|---|---|---|
| Tridev Prabhu | **Design:** refactored the verification engine into Strategy rules (`VerificationCheck` and six check classes) and Observer listeners (`VerdictIssued`, `VerdictAlertListener`, `VerificationHistoryRecorder`).<br>**Frontend:** route-policy editor in the Admin Panel (`RoutePolicyEditor`); optional scan time in the Scan Console.<br>**Testing:** `WorkflowIntegrationTest` (route, tampering, recall and wiring workflows); JaCoCo and Vitest coverage in CI; 6 frontend tests; fixed a time-zone-dependent analytics test.<br>**Reports:** reasons column in the product-history report.<br>**Docs:** Phase 3 README. | 4 (16) |
| Sanjay Gokulakrishanan | **Frontend:** alerts center with status filter, acknowledge and resolve (`Alerts`); analytics dashboard with reusable chart components (`Analytics`, `Charts`); public consumer check page with optional camera QR scanning (`PublicCheck`); printable QR labels per batch (`QrLabels`, `qr.ts`); report downloads for audit reports and product histories (`Reports`, `download()` in `api/client.ts`, `api/reports.ts`, buttons on `Verify`); menu and route wiring for the new pages.<br>**Testing:** 29 frontend tests across 6 new test files; shared test helpers (`test/helpers.tsx`); frontend coverage raised from 63% to 77% of lines.<br>**Docs:** Phase 3 feature status, scope changes and design-pattern rows for the new pages. | 8 (13) |
