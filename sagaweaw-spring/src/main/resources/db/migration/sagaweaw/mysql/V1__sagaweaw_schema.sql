-- Sagaweaw schema for MySQL 8.0+ — managed via sagaweaw_schema_history table.

CREATE TABLE IF NOT EXISTS sagas (
    id              VARCHAR(36)   NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    status          VARCHAR(50)   NOT NULL,
    context_json    JSON          NOT NULL DEFAULT (JSON_OBJECT()),
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    completed_at    DATETIME(6),
    idempotency_key VARCHAR(255),
    version         INTEGER       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX idx_sagas_idempotency (idempotency_key),
    INDEX idx_sagas_status (status),
    INDEX idx_sagas_name   (name)
);

-- ----------------------------------------------------------------

CREATE TABLE IF NOT EXISTS saga_steps (
    id             VARCHAR(36)  NOT NULL,
    saga_id        VARCHAR(36)  NOT NULL,
    step_name      VARCHAR(255) NOT NULL,
    step_order     INTEGER      NOT NULL,
    status         VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    attempt        INTEGER      NOT NULL DEFAULT 0,
    max_attempts   INTEGER      NOT NULL DEFAULT 3,
    next_retry_at  DATETIME(6),
    last_error     TEXT,
    error_trace    TEXT,
    input_payload  JSON,
    output_payload JSON,
    executed_at    DATETIME(6),
    completed_at   DATETIME(6),
    duration_ms    BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_saga_steps_saga FOREIGN KEY (saga_id) REFERENCES sagas (id),
    INDEX idx_saga_steps_saga_id (saga_id),
    INDEX idx_saga_steps_retry   (status, next_retry_at)
);

-- ----------------------------------------------------------------

CREATE TABLE IF NOT EXISTS saga_events (
    id         VARCHAR(36)  NOT NULL,
    saga_id    VARCHAR(36)  NOT NULL,
    step_name  VARCHAR(255),
    event_type VARCHAR(100) NOT NULL,
    payload    JSON,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_saga_events_saga FOREIGN KEY (saga_id) REFERENCES sagas (id),
    INDEX idx_saga_events_saga_id (saga_id),
    INDEX idx_saga_events_created (created_at)
);

-- ----------------------------------------------------------------

-- No FK on saga_id: outbox messages must be publishable even after a saga is archived.
CREATE TABLE IF NOT EXISTS outbox_messages (
    id               VARCHAR(36)  NOT NULL,
    saga_id          VARCHAR(255) NOT NULL,
    step_name        VARCHAR(255),
    topic            VARCHAR(255) NOT NULL,
    payload          JSON         NOT NULL,
    headers          JSON,
    published        TINYINT(1)   NOT NULL DEFAULT 0,
    publish_attempts INTEGER      NOT NULL DEFAULT 0,
    created_at       DATETIME(6)  NOT NULL,
    published_at     DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_outbox_unpublished (published, created_at)
);

-- ----------------------------------------------------------------

-- No FK on saga_id: dead letters persist for forensics after the saga row is removed.
CREATE TABLE IF NOT EXISTS dead_letters (
    id               VARCHAR(36)  NOT NULL,
    saga_id          VARCHAR(255) NOT NULL,
    step_name        VARCHAR(255) NOT NULL,
    error_message    TEXT,
    error_trace      TEXT,
    context_snapshot TEXT,
    created_at       DATETIME(6)  NOT NULL,
    reprocessed      TINYINT(1)   NOT NULL DEFAULT 0,
    reprocessed_at   DATETIME(6),
    reprocessed_by   VARCHAR(255),
    PRIMARY KEY (id),
    INDEX idx_dead_letters_saga_id  (saga_id),
    INDEX idx_dead_letters_unreproc (reprocessed, created_at)
);
