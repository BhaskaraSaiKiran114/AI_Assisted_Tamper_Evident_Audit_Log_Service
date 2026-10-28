-- psql-only initialization; secrets come from the container environment, not command arguments.
\set ON_ERROR_STOP on
\getenv migrator_password AUDIT_MIGRATION_PASSWORD
\getenv runtime_password AUDIT_DB_PASSWORD
ALTER ROLE audit_migrator PASSWORD :'migrator_password';
ALTER ROLE audit_runtime PASSWORD :'runtime_password';
