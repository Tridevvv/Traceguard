# Threat model

Scope: the TraceGuard server, API, ledger and simulated reader protocol. Method: list each threat, the attacker goal, the mitigation, and how we test it in Phase 4.

| ID | Threat | Attacker goal | Mitigation | Phase 4 test |
|----|--------|---------------|------------|--------------|
| T1 | Tag cloning | Make a fake product look genuine by copying a tag ID | Duplicate-presence and impossible-travel detection; per-tag signed challenge in the simulated protocol | Submit the same tag from two distant checkpoints; expect SUSPECT or COUNTERFEIT |
| T2 | Event tampering | Rewrite history to hide a diversion | SHA-256 hash chain; any edit breaks every later link | Modify a row directly in the DB; expect verification to fail |
| T3 | Forged events | Insert fake events | ECDSA/RSA signature per checkpoint key, verified at verification time | Submit an event signed with a wrong key; expect rejection |
| T4 | Replay of old scans | Resubmit a valid old scan | Timestamps, sequence numbers, per-tag unique nonces | Resend a captured scan; expect rejection |
| T5 | Unauthorized access | Read or change data without permission | JWT authentication, RBAC, audit log, bcrypt passwords, TLS | Call each endpoint with the wrong role; expect 401/403 |
| T6 | Insider manipulation | A privileged user edits or deletes records | Append-only ledger, no update/delete API, DB trigger, read-only auditor role | Attempt UPDATE/DELETE on `events`; expect an error |

## Assumptions and residual risks

- A physically genuine tag attached to a fake item is not detectable by software; TraceGuard detects abnormal movement and tampering, not physical substitution.
- Checkpoint private keys must stay secret. For this project they live in a local key store; production would use an HSM (out of scope).
- Public verification endpoints are rate limited to reduce scraping and enumeration of tag IDs.
- Denial of service is out of scope beyond basic rate limiting.
