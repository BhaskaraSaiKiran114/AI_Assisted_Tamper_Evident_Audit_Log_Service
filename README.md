# Tamper-Evident Audit Log Service

Engineer-led Java prototype. **Milestones 1-5 complete (5/8, 62.5%).** Ingestion, protected payloads, atomic hash-chain append, filtered reads/pagination, verification, JWT permissions, protected OpenAPI/Swagger and restricted PostgreSQL runtime credentials are implemented. Audited retention and selective field-key erasure are implemented; export and compliance remain planned.

## Engineering baseline

- [Requirements and Scenario C clarification](docs/requirements.md)
- [Architecture/API blueprint](docs/architecture.md) and [decisions](docs/decisions/)
- [Eight milestones](docs/milestones.md) and [traceability](docs/traceability.md)
- [Governance](docs/engineering-governance.md), [threat model](docs/threat-model.md), [testing strategy](docs/testing-strategy.md)
- [M1 validation](docs/validation/milestone-1.md), [AI usage](AI_USAGE_LOG.md), [unsigned attestation](ATTESTATION.md), [living summary](docs/final-engineering-summary.md)
- [M2 validation](docs/validation/milestone-2.md) and [exact ingestion/encoding v1 contract](docs/encoding-v1.md)
- [M3 validation](docs/validation/milestone-3.md), [query/verification contract](docs/query-verification.md), and [snapshot/cursor decision](docs/decisions/ADR-005-read-snapshots-and-pagination.md)
- [M4 validation](docs/validation/milestone-4.md), [security/API setup](docs/security-and-api.md), and [authentication/role decision](docs/decisions/ADR-006-authentication-and-runtime-roles.md)

## Prerequisites

JDK **21**, with JAVA_HOME pointing to its directory. The wrapper downloads pinned Maven 3.9.16/dependencies on first use; no global Maven needed. Optional Docker Compose requires a running daemon for PostgreSQL 17.6.

Stack: Spring Boot 4.1.0, Spring MVC/JDBC/Security Resource Server, Flyway, PostgreSQL/H2, Springdoc OpenAPI/Swagger and Boot-managed JUnit. Public references: [Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html), [Springdoc](https://springdoc.org/), [Maven wrapper](https://maven.apache.org/tools/wrapper/).

## Build and run (disposable H2)

PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& "$env:JAVA_HOME\bin\java.exe" scripts/LocalJwt.java init .tools/jwt
$env:AUDIT_JWT_PUBLIC_KEY_BASE64 = (Get-Content .tools/jwt/public-key-base64.txt -Raw).Trim()
.\mvnw.cmd spring-boot:run
```

POSIX:

```sh
export JAVA_HOME=/path/to/jdk-21
bash ./mvnw --batch-mode --no-transfer-progress clean verify
"$JAVA_HOME/bin/java" scripts/LocalJwt.java init .tools/jwt
export AUDIT_JWT_PUBLIC_KEY_BASE64="$(cat .tools/jwt/public-key-base64.txt)"
bash ./mvnw spring-boot:run
```

Or run with Java 21: `java -jar target/audit-log-service-0.1.0-SNAPSHOT.jar`.

Run key initialization once; it refuses overwriting existing keys. Reuse the existing public-key file on later starts. Default h2 keeps data/master key in memory and is not PostgreSQL proof. Server binds to 127.0.0.1:8080. GET /actuator/health is public/minimal. Business routes require valid Bearer tokens and explicit AUDIT_WRITE/READ/VERIFY; OpenAPI/Swagger including assets requires AUDIT_DOCS. Without a configured key, H2 starts but accepts no token. See the security guide for captured five-minute local tokens and authenticated API examples; no token-issuing server endpoint exists.

## PostgreSQL local profile

```powershell
Copy-Item .env.example .env
# Fill all three distinct passwords in .env before starting a NEW dedicated volume.
docker compose up -d postgres
$env:SPRING_PROFILES_ACTIVE = 'postgres'
$env:AUDIT_DB_URL = 'jdbc:postgresql://localhost:5432/auditlog'
$env:AUDIT_DB_USERNAME = 'audit_runtime'
$env:AUDIT_DB_PASSWORD = '<runtime password from .env>'
$env:AUDIT_MIGRATION_USERNAME = 'audit_migrator'
$env:AUDIT_MIGRATION_PASSWORD = '<migration password from .env>'
$env:AUDIT_MASTER_KEY_BASE64 = '<securely generated Base64-encoded 32-byte key>'
$env:AUDIT_JWT_PUBLIC_KEY_BASE64 = (Get-Content .tools/jwt/public-key-base64.txt -Raw).Trim()
.\mvnw.cmd spring-boot:run
```

Compose reads .env; Spring Boot does **not** automatically read it. Fresh-volume initialization creates separate audit_migrator/audit_runtime roles; Flyway owns/migrates the schema and V3 grants runtime append/read/head-update privileges. M5 adds V4 lifecycle tables and PostgreSQL V5 scoped erasure function; direct key DELETE remains denied. Empty example passwords deliberately prevent Compose startup. Existing volumes/role names are not automatically upgraded or reset: review the security guide before reusing earlier M1-M3 data. No destructive reset is prescribed.

Generate the payload master key once (for example `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))` in PowerShell 7) and preserve it securely outside Git/database across restarts. Losing it loses protected values. JWT public key, payload master key and optional cursor signing key have separate purposes. Default protected paths are /accountNumber,/personalIdentifier. If POSTGRES_PORT changes, update AUDIT_DB_URL. `docker compose stop` preserves data. Do not activate H2 and PostgreSQL profiles together. See security/API setup for privilege boundaries and production migration-job separation.

## Reference-database verification

Normal verify runs 85 unit/H2/security tests. postgres-it adds 41 reference cases (8 append, 16 read/verify, 13 lifecycle, 4 real runtime-role cases). It requires a fresh disposable cluster/database audit_m2_test, administrator AUDIT_TEST_DB_URL/USERNAME/PASSWORD, AUDIT_TEST_DB_DESTRUCTIVE_OK=yes and AUDIT_TEST_CLUSTER_ROLES_OK=yes. Tests create cluster-wide roles, clear/corrupt fixture data and verify denied mutations. Existing role names or missing consent cause refusal. Never target a shared server or useful data; the runner sets consent only for its new cluster.

Windows with installed PostgreSQL binaries can create, test and stop a separate temporary cluster without Docker or using an existing service:

```powershell
.\scripts\verify-postgres.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\16\bin'
```

The script uses a fresh GUID-named cluster under ignored .tools, generated local credentials, loopback port 55432, and finally stops that cluster. Select -Port if needed. JDK 21 must already be configured. Optional -MavenRepository and -Offline support a populated local cache. Temporary cluster artifacts may contain test credentials and are deliberately not committed. Locally verified PostgreSQL version: 16.3; Compose/CI targets 17.6, whose external CI run is still pending.

## Query and verification (M3)

Implemented: GET /audit/events, GET /audit/events/{eventId}, and GET /audit/verify. M4 enables them with valid JWTs and their explicit permissions. Tests cover the real security filter/decoder as well as the standalone adapter contracts; no permissive application profile exists.

Queries combine actorId/resourceType/resourceId/eventType/from/to, default to 50 results (maximum 200), and return nextCursor plus snapshotHead. Continue with the same filters and limit. Times use inclusive from/exclusive to. Signed cursors exclude later appends; set a separate AUDIT_CURSOR_KEY_BASE64 for persistence across restarts/instances, or default process-random keys invalidate old cursors after restart. Missing/changed cursor anchor is 409, invalid filters/cursor 400, missing event 404. Returned payload restores protected values; unexplained missing/invalid keys are 503. Audited erasure returns a validated redaction marker and redactedPaths.

Verification traverses all stored records and reports intact, checkedCount, snapshotHead and the first inconsistency (sequence, nullable eventId, violation type). A broken chain returns a 200 result with intact=false; storage failures return 503, never success. Read snapshots use PostgreSQL REPEATABLE_READ / H2 SERIALIZABLE without changing append isolation. See the full contract for violation precedence and trust/performance limits. Full verification includes archived records and cross-checks lifecycle evidence. External checkpoints remain planned.

## Quality gates

Normal clean verify enforces Java 21, Checkstyle, compilation, foundation/domain/security tests and packaging. postgres-it adds reference tests. M5 passed all 126 cases, including real JWTs, protected schemas/UI assets, direct tampering, snapshot concurrency, the actual local-token helper and runtime-role denials. CI is configured with Java 21 and a disposable PostgreSQL service; remote CI is not claimed executed.

```powershell
.\mvnw.cmd -Psecurity-audit verify
git diff --check
```

Opt-in security-audit uses current vulnerability data and fails on CVSS >=7. NVD_API_KEY can improve retrieval; never commit it. Normal verify is not a vulnerability scan. PostgreSQL/scenario/performance validation is tracked separately.

If enterprise TLS inspection requires Windows certificate trust, configure your organizational CA or, when appropriate for the shell:

```powershell
$env:MAVEN_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
```

Do not disable TLS verification. Project Maven settings mirror external repositories to Maven Central, avoiding extra snapshot/milestone repositories declared by transitive POMs. Bounded connection/request timeouts prevent indefinite downloads. If your organization requires a repository manager, supply your reviewed alternative with `-s path/to/settings.xml`; never put credentials in the committed settings file. Local tools/build/data/secrets/exports/IDE files are ignored.

## Ownership and submission

The candidate reviews outputs, completes their own attestation, and maintains genuine history. The assignment expects a private repository/reviewer access at submission. No publication/sharing is performed by these milestones. Do not commit the confidential assignment or real client data. Approval/evidence rules are recorded in governance and AI usage.

## Retention and redaction (M5)

See the [lifecycle contract](docs/lifecycle.md), [capability decision](docs/decisions/ADR-007-audited-lifecycle-capabilities.md) and [M5 evidence](docs/validation/milestone-5.md) for exact commands. POST /audit/retention/runs and POST /audit/events/{eventId}/redactions require AUDIT_LIFECYCLE. Commands require operationId/reason; erasure additionally requires paths, approvalReference and confirmation=ERASE_FIELD_KEYS. Exact retries return the original receipt; changed intent with the same ID is 409. Approval references are operator assertions, not externally validated approval.

Retention defaults to P90D, policy v1 and batches of 100; set AUDIT_RETENTION_WINDOW, AUDIT_RETENTION_POLICY_VERSION and AUDIT_RETENTION_BATCH_SIZE as shell variables. Archived records are hidden by default; includeArchived=true requires both READ and LIFECYCLE permissions. Cursor v2 binds archive mode and rejects v1 cursors. Existing reserved namespace collisions need upgrade review. Keys are removed only from the active transactional store: backups, WAL, replicas, earlier snapshots/in-flight reads and previously delivered values limit erasure. No physical purge, automatic scheduler or compliance certification is claimed.
