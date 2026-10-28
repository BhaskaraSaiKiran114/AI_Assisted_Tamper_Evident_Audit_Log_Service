-- Run as the administrator of a NEW dedicated database/cluster before Flyway.
-- Intentionally refuses existing role names; do not run against a shared server.
CREATE ROLE audit_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE audit_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO audit_migrator;
GRANT USAGE ON SCHEMA public TO audit_runtime;
DO $$
BEGIN
    EXECUTE format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database());
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO audit_migrator, audit_runtime', current_database());
END
$$;
