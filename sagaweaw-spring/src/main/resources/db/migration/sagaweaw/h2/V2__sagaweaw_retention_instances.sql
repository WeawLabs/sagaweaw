-- Task 61: instance_id
ALTER TABLE sagas ADD COLUMN IF NOT EXISTS instance_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_sagas_instance ON sagas (instance_id, updated_at);

-- Task 60: sagas_archive
CREATE TABLE IF NOT EXISTS sagas_archive (
    id              VARCHAR(36)              NOT NULL,
    name            VARCHAR(255)             NOT NULL,
    status          VARCHAR(50)              NOT NULL,
    context_json    CLOB                     NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at    TIMESTAMP WITH TIME ZONE,
    idempotency_key VARCHAR(255),
    version         INTEGER                  NOT NULL DEFAULT 0,
    instance_id     VARCHAR(255),
    steps_snapshot  CLOB,
    archived_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sagas_archive_name        ON sagas_archive (name);
CREATE INDEX IF NOT EXISTS idx_sagas_archive_archived_at ON sagas_archive (archived_at);
