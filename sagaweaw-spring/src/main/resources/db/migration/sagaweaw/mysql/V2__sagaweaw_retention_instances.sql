-- Task 61: instance_id
ALTER TABLE sagas ADD COLUMN instance_id VARCHAR(255);
CREATE INDEX idx_sagas_instance ON sagas (instance_id, updated_at);

-- Task 60: sagas_archive
CREATE TABLE IF NOT EXISTS sagas_archive (
    id              VARCHAR(36)   NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    status          VARCHAR(50)   NOT NULL,
    context_json    LONGTEXT      NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    completed_at    DATETIME(6),
    idempotency_key VARCHAR(255),
    version         INTEGER       NOT NULL DEFAULT 0,
    instance_id     VARCHAR(255),
    steps_snapshot  LONGTEXT,
    archived_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_sagas_archive_name        (name),
    INDEX idx_sagas_archive_archived_at (archived_at)
);
