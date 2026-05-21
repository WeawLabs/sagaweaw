-- Sagaweaw schema — managed independently via sagaweaw_schema_history table.
-- Runs automatically at startup when flyway-core is on the classpath.

CREATE TABLE IF NOT EXISTS sagas (
    id              VARCHAR(36)                  NOT NULL,
    name            VARCHAR(255)                 NOT NULL,
    status          VARCHAR(50)                  NOT NULL,
    context_json    JSONB                        NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    completed_at    TIMESTAMP(6) WITH TIME ZONE,
    idempotency_key VARCHAR(255),
    version         INTEGER                      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sagas_idempotency
    ON sagas (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sagas_status ON sagas (status);
CREATE INDEX IF NOT EXISTS idx_sagas_name   ON sagas (name);

-- ----------------------------------------------------------------

CREATE TABLE IF NOT EXISTS saga_steps (
    id             VARCHAR(36)                  NOT NULL,
    saga_id        VARCHAR(36)                  NOT NULL,
    step_name      VARCHAR(255)                 NOT NULL,
    step_order     INTEGER                      NOT NULL,
    status         VARCHAR(50)                  NOT NULL DEFAULT 'PENDING',
    attempt        INTEGER                      NOT NULL DEFAULT 0,
    max_attempts   INTEGER                      NOT NULL DEFAULT 3,
    next_retry_at  TIMESTAMP(6) WITH TIME ZONE,
    last_error     TEXT,
    error_trace    TEXT,
    input_payload  JSONB,
    output_payload JSONB,
    executed_at    TIMESTAMP(6) WITH TIME ZONE,
    completed_at   TIMESTAMP(6) WITH TIME ZONE,
    duration_ms    BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_saga_steps_saga FOREIGN KEY (saga_id) REFERENCES sagas (id)
);

CREATE INDEX IF NOT EXISTS idx_saga_steps_saga_id ON saga_steps (saga_id);
CREATE INDEX IF NOT EXISTS idx_saga_steps_retry   ON saga_steps (status, next_retry_at);

-- ----------------------------------------------------------------

CREATE TABLE IF NOT EXISTS saga_events (
    id         VARCHAR(36)                  NOT NULL,
    saga_id    VARCHAR(36)                  NOT NULL,
    step_name  VARCHAR(255),
    event_type VARCHAR(100)                 NOT NULL,
    payload    JSONB,
    created_at TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_saga_events_saga FOREIGN KEY (saga_id) REFERENCES sagas (id)
);

CREATE INDEX IF NOT EXISTS idx_saga_events_saga_id ON saga_events (saga_id);
CREATE INDEX IF NOT EXISTS idx_saga_events_created ON saga_events (created_at);

-- ----------------------------------------------------------------

-- No FK on saga_id: outbox messages must be publishable even after a saga is archived.
CREATE TABLE IF NOT EXISTS outbox_messages (
    id               VARCHAR(36)                  NOT NULL,
    saga_id          VARCHAR(255)                 NOT NULL,
    step_name        VARCHAR(255),
    topic            VARCHAR(255)                 NOT NULL,
    payload          JSONB                        NOT NULL,
    headers          JSONB,
    published        BOOLEAN                      NOT NULL DEFAULT FALSE,
    publish_attempts INTEGER                      NOT NULL DEFAULT 0,
    created_at       TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    published_at     TIMESTAMP(6) WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished ON outbox_messages (published, created_at);

-- ----------------------------------------------------------------

-- No FK on saga_id: dead letters persist for forensics after the saga row is removed.
CREATE TABLE IF NOT EXISTS dead_letters (
    id               VARCHAR(36)                  NOT NULL,
    saga_id          VARCHAR(255)                 NOT NULL,
    step_name        VARCHAR(255)                 NOT NULL,
    error_message    TEXT,
    error_trace      TEXT,
    context_snapshot TEXT,
    created_at       TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    reprocessed      BOOLEAN                      NOT NULL DEFAULT FALSE,
    reprocessed_at   TIMESTAMP(6) WITH TIME ZONE,
    reprocessed_by   VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_dead_letters_saga_id  ON dead_letters (saga_id);
CREATE INDEX IF NOT EXISTS idx_dead_letters_unreproc ON dead_letters (reprocessed, created_at);
