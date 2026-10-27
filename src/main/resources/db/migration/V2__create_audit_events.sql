CREATE TABLE audit_event (
    event_id UUID PRIMARY KEY,
    event_sequence BIGINT NOT NULL UNIQUE CHECK (event_sequence > 0),
    event_type VARCHAR(255) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    resource_type VARCHAR(255) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    canonical_bytes BYTEA NOT NULL,
    payload_json TEXT NOT NULL,
    previous_hash VARCHAR(64) NOT NULL CHECK (CHAR_LENGTH(previous_hash) = 64),
    record_hash VARCHAR(64) NOT NULL CHECK (CHAR_LENGTH(record_hash) = 64)
);

-- Keys are wrapped using a master key supplied outside this database.
CREATE TABLE payload_key (
    event_id UUID NOT NULL REFERENCES audit_event(event_id),
    field_path VARCHAR(1024) NOT NULL,
    key_version INTEGER NOT NULL CHECK (key_version = 1),
    wrapped_key TEXT NOT NULL,
    PRIMARY KEY (event_id, field_path)
);

CREATE INDEX idx_audit_actor_sequence ON audit_event(actor_id, event_sequence);
CREATE INDEX idx_audit_resource_sequence ON audit_event(resource_type, resource_id, event_sequence);
CREATE INDEX idx_audit_type_sequence ON audit_event(event_type, event_sequence);
CREATE INDEX idx_audit_occurred_at ON audit_event(occurred_at);
