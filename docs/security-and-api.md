# Security and API contract - milestone 4

M4 enables the implemented write/read/verification APIs through stateless JWT authentication. Health remains public. Lifecycle, export and compliance endpoints are still denied pending their own milestones.

## JWT trust contract

- Trust one explicitly configured Base64-encoded X.509/SPKI RSA public key (AUDIT_JWT_PUBLIC_KEY_BASE64), minimum 2048 bits. Only RS256 is accepted; no unsigned, HMAC or other RSA algorithm fallback.
- Match AUDIT_JWT_ISSUER exactly (local default urn:audit-log:local) and require AUDIT_JWT_AUDIENCE in aud (default audit-log-service).
- Require sub, exp, nbf and iat. Subject is nonblank and at most 255 UTF-16 units. exp must be after iat; nbf cannot follow exp; iat cannot be more than 30 seconds in the future. Timestamp validation checks expiration/not-before with 30-second clock skew.
- permissions is an optional array of strings. A missing array grants no authority; malformed shape invalidates the token. Unknown strings grant nothing. scope, roles, ROLE_ADMIN and SCOPE_ prefixes do not confer permissions.
- Only Authorization: Bearer is accepted. Cookies, form fields and query-string tokens are ignored. No HTTP Basic, form login, application token-issuing endpoint, session or browser ambient credential is enabled. CSRF is disabled for this header-only model; future cookie authentication would require a new review.
- Persistent PostgreSQL configuration fails startup without a valid public key. Disposable H2 can start without a configured key, but its rejecting decoder authenticates no token. This is a fail-closed setup convenience, not a bypass or auto-issued developer credential.

The application holds only the verification public key. The local developer helper holds an ignored private key outside the application; deployed signing/identity management belongs to the trusted issuer. Key rotation currently requires replacing the configured public key and restarting. No multi-key rollover, online discovery, JWKS fetch, refresh-token flow or fine-grained token revocation is implemented. Keep deployed private keys outside this service and Git.

## Permissions and routes

| Request | Required permission |
| --- | --- |
| GET /actuator/health | Public without a token |
| POST /audit/events | AUDIT_WRITE |
| GET /audit/events, GET /audit/events/{eventId} | AUDIT_READ |
| GET /audit/verify | AUDIT_VERIFY |
| GET /v3/api-docs, /v3/api-docs.yaml, /v3/api-docs/** | AUDIT_DOCS |
| GET /swagger-ui.html, /swagger-ui/** including assets | AUDIT_DOCS |
| Any unlisted method/path | Denied |

Permissions have no implicit hierarchy. AUDIT_DOCS does not read event payloads; AUDIT_READ does not append or verify. Recognizing future AUDIT_LIFECYCLE/AUDIT_EXPORT/AUDIT_COMPLIANCE claims does not enable their unimplemented endpoints. PUT/PATCH/DELETE are not event APIs and remain denied even with every permission.

This is a single-tenant, service-wide permission model. AUDIT_READ can retrieve all recorded resources and decrypt configured payload values. The authenticated subject identifies the producer; actorId remains a producer assertion, which may represent another actor. No claim of upstream completeness or independent actor identity attestation is made.

## HTTP error and resource policy

Missing/invalid tokens return 401 with a Bearer challenge and a generic application/problem+json body. Valid authentication without permission returns 403. Errors never echo a token, signature, validation reason, private key or request body. Known request/availability errors retain the M2/M3 safe problem-detail handling. Standard Spring security headers remain enabled; responses prevent caching, and stateless requests do not create sessions.

Relevant application statuses: 201 append; 200 query/lookup/verification; 400 invalid event/filter/cursor/UUID; 404 missing event; 409 unavailable pagination anchor; 413 oversized request; 429 verification capacity; 503 storage/transaction/integrity/key availability failure. A broken chain returns 200 with intact=false and first inconsistency; operational errors never become integrity success. Unsupported HTTP media types/framework routing errors follow Spring MVC's own error handling; not every framework error is claimed to be an application-defined problem schema.

Full verification has a per-process nonblocking semaphore (default 2 active scans, AUDIT_VERIFY_MAX_CONCURRENT range 1-16). Excess calls return 429 before database work; capacity is released after success or failure. This is a local concurrency budget, not a distributed rate limit or CPU/wall-clock SLA. Request/depth/page bounds and database transaction timeouts remain in force. Deployment-level quotas, benchmarks and future export limits remain later work.

## OpenAPI and Swagger

OpenAPI JSON/YAML and the Swagger HTML/config/assets are protected with AUDIT_DOCS. The document describes only implemented /audit operations, a bearer security scheme, the strict write schema, query parameters, result types and x-required-permission metadata. New operations require an explicit mapping in OpenApiConfiguration. Request validation still uses the strict parser; the schema/descriptions document constraints that JSON Schema cannot express exactly, including duplicate-key rejection, depth, UTF-16 limits and decimal normalization.

Swagger does not persist authorization and does not load the default Petstore definition. Use an authenticated HTTP client or a browser configured to send the Bearer header for UI, config and asset requests. Ordinary unauthenticated browser navigation returns 401. No login page, cookie exchange or token-in-URL shortcut has been introduced. Once an authenticated browser can load the UI, its Authorize control supplies bearer authentication for API calls; the same token needs the corresponding business permissions.

## Local demonstration with Java 21

Run once from the project root (choose an unused directory; existing keys are never overwritten):

```powershell
& "$env:JAVA_HOME\bin\java.exe" scripts/LocalJwt.java init .tools/jwt
$env:AUDIT_JWT_PUBLIC_KEY_BASE64 = (Get-Content .tools/jwt/public-key-base64.txt -Raw).Trim()
.\mvnw.cmd spring-boot:run
```

In a second terminal with the same Java 21 setup:

```powershell
$token = & "$env:JAVA_HOME\bin\java.exe" scripts/LocalJwt.java token .tools/jwt 'AUDIT_WRITE,AUDIT_READ,AUDIT_VERIFY,AUDIT_DOCS'
$headers = @{ Authorization = "Bearer $token" }
$event = '{"eventType":"READ","actorId":"synthetic-actor","resourceType":"ACCOUNT","resourceId":"synthetic-resource","payload":{"accountNumber":"synthetic-only"}}'
Invoke-RestMethod http://127.0.0.1:8080/audit/events -Method Post -Headers $headers -ContentType application/json -Body $event
Invoke-RestMethod http://127.0.0.1:8080/audit/events -Headers $headers
Invoke-RestMethod http://127.0.0.1:8080/audit/verify -Headers $headers
Invoke-RestMethod http://127.0.0.1:8080/v3/api-docs -Headers $headers
```

The source launcher has no external dependencies, creates an RSA pair with owner-only private-file permissions (POSIX 0600 or Windows owner ACL), and produces five-minute tokens for the local issuer/audience. The helper refuses paths outside ignored .tools and refuses overwriting keys. Tokens are captured in a variable here; never paste actual tokens/private keys into Git, AI logs or shared terminal output. This is a local development utility, not a deployed identity provider. A test executes this actual helper and validates its output with the real decoder.

## PostgreSQL roles and bootstrap

Use a dedicated fresh database/cluster. Bootstrap scripts intentionally refuse existing role names:

1. scripts/postgres/01-roles.sql runs as its administrator, creates LOGIN audit_migrator/audit_runtime with no superuser/create-role/create-db/replication/bypass-RLS capabilities, removes public schema creation and database TEMP/CONNECT grants, makes the migrator own public, and gives both logins CONNECT with runtime schema USAGE.
2. scripts/postgres/02-passwords.sql is a psql initialization file. It reads AUDIT_MIGRATION_PASSWORD/AUDIT_DB_PASSWORD from environment using psql variables and assigns quoted passwords; secrets are not embedded in committed SQL or command arguments.
3. Flyway connects as audit_migrator. PostgreSQL-only V3 grants runtime SELECT/INSERT on audit_event and payload_key, SELECT on chain_head, and UPDATE only on its last_sequence/last_hash columns. Runtime owns no tables and cannot read Flyway history, alter/create/drop/truncate objects, update/delete events, delete keys/head, create temporary tables or assume the migrator role.

Column-limited head UPDATE permits the append lock/head advance. Event history remains append-only for runtime credentials. Lifecycle-specific key deletion privileges are intentionally absent until M5's reviewed design. H2 has no claim of equivalent database-role hardening.

Compose initializes these scripts only for a **new** data volume. Fill three distinct nonempty passwords in ignored .env (admin, migration, runtime); blank examples deliberately fail startup. Spring uses AUDIT_DB_USERNAME=audit_runtime / AUDIT_DB_PASSWORD and separate AUDIT_MIGRATION_USERNAME=audit_migrator / AUDIT_MIGRATION_PASSWORD. It also needs the existing payload master key and the JWT public key. .env is read by Compose, not automatically by Spring.

Existing M1-M3 volumes/databases are not automatically taken over or reset. Their owner/schema permissions and roles need an explicit reviewed upgrade; these scripts refuse pre-existing names. Preserve existing data and master keys. No volume deletion/reset or existing-service operation is part of M4.

Startup Flyway credentials are separate from the runtime pool, but a compromised application process could access those startup secrets. A production deployment should run migrations in a separate job and start the runtime without migration credentials (Flyway disabled). This prototype does not certify that operational separation or production key management.

## Validation and boundaries

SecurityIntegrationTest signs real RSA tokens and exercises the real resource-server filter/decoder: permission matrix, invalid signature/algorithm/time/issuer/audience/required claims, non-header tokens, protected docs/assets, end-to-end audit flow and verification concurrency/failure release. PostgreSQL tests use the actual restricted Spring datasource and separate migration login; destructive tampering suites retain an explicitly isolated administrator fixture.

Reference tests require both AUDIT_TEST_DB_DESTRUCTIVE_OK=yes and AUDIT_TEST_CLUSTER_ROLES_OK=yes, plus a fresh disposable cluster/database named audit_m2_test. The runner supplies these only for its new cluster; CI uses its ephemeral service. Never use a shared server: roles are cluster-wide. Missing consent fails before connecting; existing role names cause refusal rather than credential replacement.

Current proof is local H2/PostgreSQL 16.3. Compose/CI 17.6 execution, dependency advisory scan, performance/load evidence, lifecycle/export/compliance and production readiness remain later work. See the M4 validation ledger and ADR-006. Public implementation references: [Spring JWT resource server](https://www.springframework.org/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [PostgreSQL privileges](https://www.postgresql.org/docs/16/ddl-priv.html).
