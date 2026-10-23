# Requirements

Source: TraceGuard Software Requirements Specification v1.0 (October 2026). IDs are used in commits, issues and tests.

## Roles

| Role | Capabilities |
|------|--------------|
| ADMIN | Manage organizations, users, checkpoints, route policies, keys |
| MANUFACTURER | Register products and batches, issue tags, recall a batch |
| DISTRIBUTOR | Scan products at warehouse and transit checkpoints |
| RETAILER | Scan on receipt, verify before sale |
| CONSUMER | Public, read-only verification by tag ID or QR code (no login) |
| AUDITOR | Read-only access to ledgers, alerts, reports |

## Functional requirements

| ID | Requirement | Phase |
|----|-------------|-------|
| FR-1 | User registration and login with JWT and role-based access | 2 |
| FR-2 | Create organizations, checkpoints and route policies | 2 |
| FR-3 | Register products and batches; generate unique tag IDs and QR codes | 2 |
| FR-4 | Accept scans from readers/simulator and create signed, hash-linked events | 2 |
| FR-5 | Store events in an append-only ledger with no update or delete | 2 |
| FR-6 | Verify a product: signatures, hash chain, route order, timing | 3 |
| FR-7 | Detect anomalies: cloned tag, impossible travel, unregistered tag, unauthorized location, scan burst | 3 |
| FR-8 | Return AUTHENTIC / SUSPECT / COUNTERFEIT with machine-readable reasons | 3 |
| FR-9 | Raise, display and manage alerts; email on high severity | 3 |
| FR-10 | Flag or recall all products in a batch | 3 |
| FR-11 | Public consumer verification by tag ID or QR code | 3 |
| FR-12 | Analytics: scan volume, verdict distribution, alerts by type and checkpoint | 3 |
| FR-13 | Export audit reports as PDF/CSV | 3 |
| FR-14 | Log all user actions for audit | 2 |

## Non-functional requirements

| ID | Requirement |
|----|-------------|
| NFR-1 | Verification completes within 2 seconds |
| NFR-2 | Scan ingestion handles at least 100 scans per second |
| NFR-3 | Any change to a past event is detectable |
| NFR-4 | TLS for all traffic; passwords hashed with bcrypt |
| NFR-5 | Clean UI that works on desktop and mobile |
| NFR-6 | At least 80% unit test coverage on the verification engine and ledger |
| NFR-7 | 99% availability target for the demo deployment |

## Out of scope

Physical RFID hardware procurement, payment or logistics-provider integration, production-grade hardware security modules.
