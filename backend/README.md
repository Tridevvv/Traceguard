# Backend

Java 17, Spring Boot 3.5 (Web, Security, Data JPA), JWT + role-based access, ECDSA-signed hash-chained ledger.

## Run locally (no Docker needed)

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile uses an in-memory H2 database (data is lost on restart) and creates a local-only admin:
`admin@traceguard.local` / `DevAdmin#2026`. Swagger UI: http://localhost:8080/swagger-ui.html

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

## Try it

1. Log in as admin: `POST /api/auth/login`
2. `POST /api/organizations`, `POST /api/checkpoints`, `POST /api/auth/register` (manufacturer, distributor)
3. As the manufacturer: `POST /api/products`, `POST /api/products/{id}/batches`, `POST /api/batches/{id}/tags?count=5`
4. `POST /api/scans/simulate` at two checkpoints, then `GET /api/tags/{tagId}/chain-check`

## Notes

- The "no UPDATE/DELETE on events" rule is enforced in code (immutable entity, repository without those methods).
  The PostgreSQL trigger in `database/schema.sql` is not applied yet because Hibernate creates the tables for now.
  Switching to Flyway migrations, which can include the trigger, is a Phase 3 task.
- `POST /api/scans` currently uses a staff JWT; checkpoint-credential authentication for real readers comes later.
