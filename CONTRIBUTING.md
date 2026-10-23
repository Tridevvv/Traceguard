# Contributing to TraceGuard

## Workflow

1. Pull the latest `main`: `git checkout main && git pull`
2. Create a branch: `git checkout -b <type>/<short-name>` (e.g. `feat/ledger-service`, `docs/threat-model`, `fix/jwt-expiry`)
3. Commit small, focused changes.
4. Push and open a pull request into `main`.
5. The other teammate reviews. Merge with "Squash and merge" once approved.

Do not push directly to `main`.

## Commit messages

Use the form `type: short summary`, where type is one of `feat`, `fix`, `docs`, `test`, `refactor`, `chore`.

Examples: `feat: add hash-chained event ledger`, `docs: add threat model`.

## Code style

- Java: standard Google/Spring conventions, 4-space indent, one public class per file, no wildcard imports.
- TypeScript: strict mode, functional React components, Prettier defaults.
- SQL: lowercase snake_case, one migration per change.
- Never commit secrets, private keys, or `.env` files.

## Testing

- Backend: JUnit 5 + Mockito. Target 80% coverage on the verification engine and ledger.
- Frontend: Jest.
- A pull request should not reduce coverage on those two modules.

## Suggested ownership

Ownership avoids merge conflicts; either person can still review everything.

| Area | Lead |
|------|------|
| Backend core: auth/RBAC, ledger, crypto | Tridev |
| Backend: verification engine, anomaly detection, alerts | Tridev |
| Frontend: dashboards, scan console, verification page | Sanjay |
| Database schema, seed data, DevOps (Docker, CI) | Sanjay |
| Docs, threat model, test plans | Shared |

Adjust this table as the project evolves.
