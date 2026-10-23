# TraceGuard

[![CI](https://github.com/Tridevvv/Traceguard/actions/workflows/ci.yml/badge.svg)](https://github.com/Tridevvv/Traceguard/actions/workflows/ci.yml)

**Secure supply chain verification using RFID.** Every scan of a product becomes a digitally signed event, linked to the previous one by a SHA-256 hash, forming a tamper-evident chain. A verification engine and anomaly detector return an `AUTHENTIC`, `SUSPECT` or `COUNTERFEIT` verdict with reasons.

- Course: CS 5010 Programming Design Paradigm
- Team: Tridev Prabhu, Sanjay Gokulakrishanan
- Standard alignment: NIST SP 800-183 (Networks of Things)

## Project phases

| # | Phase | Weeks | Status |
|---|-------|-------|--------|
| 1 | Planning and Design | 1-2 | In progress |
| 2 | Development: Core Features | 3-4 | Done |
| 3 | Development: Verification and Intelligence | 5-7 | Not started |
| 4 | Testing and QA | 8-9 | Not started |
| 5 | Deployment and Documentation | 10 | Not started |

Detailed checklists are in [docs/phases.md](docs/phases.md).

## Repository layout

```
backend/     Java 17 + Spring Boot API (Phase 2+)
frontend/    React + TypeScript web app (Phase 2+)
database/    PostgreSQL schema and seed data
docs/        Requirements, architecture, UML, threat model, API design
.github/     PR and issue templates
```

## Documentation index

- [Requirements](docs/requirements.md)
- [Architecture and NIST SP 800-183 mapping](docs/architecture.md)
- [UML diagrams](docs/uml.md)
- [Database schema](docs/database-schema.md) (SQL in [database/schema.sql](database/schema.sql))
- [API design](docs/api-design.md)
- [Threat model](docs/threat-model.md)
- [Wireframes](docs/wireframes.md)

## Local development setup

Prerequisites: Git, JDK 17+, Maven (or Gradle), Node.js 24 LTS (22.22+ also works), Docker Desktop.

```bash
git clone <repo-url>
cd TraceGuard
docker compose up -d        # PostgreSQL on 5432, Redis on 6379
```

The backend and frontend setup steps are added in Phase 2.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Short version: branch off `main`, open a pull request, get one review.
