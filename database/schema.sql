-- TraceGuard schema (draft v0.1, PostgreSQL 16).
-- Becomes a migration (Flyway/Liquibase) once the backend exists.

CREATE TABLE organizations (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL UNIQUE,
    type        VARCHAR(30)  NOT NULL CHECK (type IN ('MANUFACTURER','DISTRIBUTOR','RETAILER','OTHER')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT REFERENCES organizations(id),
    email           VARCHAR(254) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,  -- bcrypt
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN','MANUFACTURER','DISTRIBUTOR','RETAILER','AUDITOR')),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE checkpoints (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations(id),
    name            VARCHAR(200) NOT NULL,
    location_name   VARCHAR(200) NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    public_key      TEXT         NOT NULL,  -- PEM, used to verify event signatures
    api_credential_hash VARCHAR(100) NOT NULL,  -- bcrypt of reader credential
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE route_policies (
    id             BIGSERIAL PRIMARY KEY,
    product_type   VARCHAR(100) NOT NULL UNIQUE,
    max_total_hours INTEGER     NOT NULL CHECK (max_total_hours > 0)
);

-- Approved checkpoint order for a policy
CREATE TABLE route_policy_steps (
    policy_id      BIGINT  NOT NULL REFERENCES route_policies(id) ON DELETE CASCADE,
    step_order     INTEGER NOT NULL,
    checkpoint_id  BIGINT  NOT NULL REFERENCES checkpoints(id),
    max_hours_from_previous INTEGER,
    PRIMARY KEY (policy_id, step_order)
);

CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations(id),
    name            VARCHAR(200) NOT NULL,
    product_type    VARCHAR(100) NOT NULL REFERENCES route_policies(product_type),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE batches (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES products(id),
    label       VARCHAR(100) NOT NULL,
    recalled    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tags (
    tag_id      VARCHAR(64) PRIMARY KEY,   -- unique tag ID encoded in RFID/QR
    batch_id    BIGINT      NOT NULL REFERENCES batches(id),
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only, hash-chained ledger
CREATE TABLE events (
    id            BIGSERIAL PRIMARY KEY,
    tag_id        VARCHAR(64) NOT NULL REFERENCES tags(tag_id),
    checkpoint_id BIGINT      NOT NULL REFERENCES checkpoints(id),
    event_type    VARCHAR(30) NOT NULL CHECK (event_type IN ('REGISTERED','RECEIVED','IN_TRANSIT','DELIVERED')),
    occurred_at   TIMESTAMPTZ NOT NULL,
    seq           INTEGER     NOT NULL,           -- 1,2,3... per tag
    nonce         VARCHAR(64) NOT NULL,
    prev_hash     CHAR(64)    NOT NULL,           -- 64 zeros for the first event
    hash          CHAR(64)    NOT NULL,
    signature     BYTEA       NOT NULL,
    UNIQUE (tag_id, seq),
    UNIQUE (tag_id, nonce)
);
CREATE INDEX idx_events_tag ON events (tag_id, seq);
CREATE INDEX idx_events_time ON events (occurred_at);

CREATE FUNCTION reject_event_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'events table is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER events_no_update_delete
    BEFORE UPDATE OR DELETE ON events
    FOR EACH ROW EXECUTE FUNCTION reject_event_change();

CREATE TABLE alerts (
    id          BIGSERIAL PRIMARY KEY,
    tag_id      VARCHAR(64) REFERENCES tags(tag_id),
    code        VARCHAR(40) NOT NULL,   -- e.g. DUPLICATE_PRESENCE
    severity    VARCHAR(10) NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH')),
    status      VARCHAR(15) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED')),
    details     JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_alerts_status ON alerts (status, severity);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    action      VARCHAR(100) NOT NULL,
    target      VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
