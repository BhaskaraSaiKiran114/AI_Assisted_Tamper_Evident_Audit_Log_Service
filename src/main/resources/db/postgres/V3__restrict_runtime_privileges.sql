-- Bootstrap roles must exist first. Flyway connects as audit_migrator, never as the runtime login.
REVOKE ALL ON TABLE audit_event, payload_key, chain_head FROM PUBLIC, audit_runtime;
GRANT SELECT, INSERT ON TABLE audit_event, payload_key TO audit_runtime;
GRANT SELECT ON TABLE chain_head TO audit_runtime;
GRANT UPDATE (last_sequence, last_hash) ON TABLE chain_head TO audit_runtime;
-- Future tables receive no implicit runtime access; grant capabilities in new reviewed migrations.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC;
