CREATE TABLE lifecycle_action (
    operation_id UUID PRIMARY KEY,
    action_type VARCHAR(16) NOT NULL CHECK (action_type IN ('RETENTION', 'REDACTION')),
    actor_id VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL CHECK (CHAR_LENGTH(request_hash) = 64),
    audit_event_id UUID NOT NULL UNIQUE REFERENCES audit_event(event_id),
    completed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    detail_json TEXT NOT NULL
);

CREATE TABLE event_archive (
    event_id UUID PRIMARY KEY REFERENCES audit_event(event_id),
    operation_id UUID NOT NULL REFERENCES lifecycle_action(operation_id),
    archived_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE payload_redaction (
    event_id UUID NOT NULL REFERENCES audit_event(event_id),
    field_path VARCHAR(1024) NOT NULL,
    operation_id UUID NOT NULL REFERENCES lifecycle_action(operation_id),
    redacted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (event_id, field_path)
);
CREATE INDEX idx_archive_operation ON event_archive(operation_id);
CREATE INDEX idx_redaction_operation ON payload_redaction(operation_id);
