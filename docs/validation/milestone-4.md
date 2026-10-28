# Milestone 4 validation and delivery evidence

Date: 2026-10-28 (Asia/Calcutta). Status: M4 complete; human acceptance pending. Authorization: user instructed "now implement Milestone 4". Planning progress: 4/8 milestones (50%), not an accuracy/reviewer score.

## Delivered scope

JWT resource server with pinned RS256 RSA public key, issuer/audience/subject/time validation, explicit permission mapping, bearer-only stateless routes, safe 401/403 errors, protected OpenAPI/Swagger and write/query/result schemas. Added a bounded full-verification semaphore and dependency-free local token helper with owner-only private-file permissions. PostgreSQL uses a separate migration login and restricted runtime pool; fresh-cluster bootstrap plus new PostgreSQL V3 migration define privileges. Applied V1/V2 were not changed. No M5 lifecycle, M6 export or M7 reporting endpoint was enabled.

## Executed environment and command

Temurin 21.0.12.1+1, Maven 3.9.16, Boot 4.1.0, H2 2.4.240 and PostgreSQL 16.3. Used offline project-local caches. The existing test runner created a fresh GUID-named cluster under ignored .tools, generated a SCRAM administrator password and supplied both disposable-database and cluster-role consent flags. It did not use the existing PostgreSQL service.

```powershell
$env:JAVA_HOME = '<project>\.tools\jdk21\jdk-21.0.12.1+1'
$env:MAVEN_USER_HOME = '<project>\.tools\maven-user-home'
.\scripts\verify-postgres.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\16\bin' `
    -MavenRepository '.tools/repository' -Offline
```

The runner executes `mvnw.cmd -Ppostgres-it clean verify`; temporary database remains named audit_m2_test for compatibility with the explicit guard. Fixture creates roles using the actual scripts/postgres/01-roles.sql, supplies random test-only login passwords and runs Flyway as audit_migrator. Append/read tamper contracts use an isolated administrator connection for their destructive setup. PostgresRuntimeSecurityIT uses the real Spring audit_runtime datasource for business behavior and denial tests; setup alone uses the guarded administrator connection. The runner stopped its own cluster normally after completion.

| Check | Result |
| --- | --- |
| Full clean verify | BUILD SUCCESS, exit 0; reported 01:11 min, completed 08:37:14 Asia/Calcutta |
| Final documentation/schema regression | Normal clean verify passed, 63 cases, 0 failures/errors/skips; reported 01:15 min, completed 08:50:54. Made receipt and per-operation problem/error response schemas explicit; auth/database behavior unchanged, so prior reference evidence remains applicable |
| Java/Maven enforcement and compiler | Passed; Java release 21 |
| Checkstyle | Zero violations in main/test source |
| Normal Surefire suite | 63 tests: previous 47 + 14 signed-token/filter-chain security cases + 2 configuration/actual helper tests |
| PostgreSQL Failsafe suite | 26 tests: previous 24 append/read/verification cases + 2 actual runtime role cases |
| Total | 89 tests, 0 failures, 0 errors, 0 skips |
| Flyway | H2 V1/V2; PostgreSQL V1/V2/V3 under migration login; separate runtime datasource |
| Package | Executable Boot JAR produced |
| Cleanup | Temporary cluster logged server stopped; no existing service/data/volume reset |

## Security evidence

- Real RSA-signed JWTs pass through the actual Spring resource-server filter and decoder; no mocked-authentication shortcut establishes acceptance. Seven individual permission cases prove positive/negative WRITE/READ/VERIFY/DOCS boundaries and deny future-role access to implemented operations.
- Nineteen invalid-token variants cover malformed input, wrong signature, RS512/HS256/none, wrong issuer/audience, expired/future timestamps, missing subject/time/issuer/audience and malformed permissions. All return generic 401 without echoing token material. Unknown permissions, scope/roles and query/form/cookie tokens grant no access.
- Authenticated POST/query/lookup/verification work; tampering still returns intact=false. Unsupported mutations/unimplemented routes remain denied. Missing/invalid configuration and a 1024-bit RSA key fail decoder initialization. The H2 unconfigured fallback accepts no token.
- OpenAPI JSON/YAML/config and Swagger HTML/CSS reject anonymous callers and non-DOCS permissions. Authorized callers receive real generated schemas and per-operation permission metadata. No external browser login flow or token-in-URL convenience is claimed.
- A latch-controlled scan proves 429 when per-process verification capacity is occupied; success/failure both release permits. Default is 2; the test sets 1. No distributed rate limit/benchmark claim.
- The actual LocalJwt.java source launcher generates owner-restricted keys, mints a five-minute token and has its output accepted by the real decoder. Windows ACL behavior was exercised; POSIX 0600 logic was compiled but awaits Linux/CI execution.
- PostgreSQL runtime appends, decrypts queries, verifies and serves protected OpenAPI. Eleven actual SQL attempts fail with 42501: event UPDATE/DELETE/TRUNCATE, ALTER/DROP/CREATE, temporary-table creation, key/head deletion, Flyway-history reading and migrator-role escalation. All four tables are owned by audit_migrator; runtime has no superuser/create-role/create-db/bypass-RLS powers.

## Failures and repairs

1. An initial baseline run passed the existing 47 cases after security wiring, before new tests were added.
2. New helper test compilation failed because a local variable named java shadowed the package name. Renamed it javaExecutable.
3. A 63-case run had one helper failure. Running the real helper exposed a wrong PosixFilePermissions.asFileAttribute argument; changed it to use fromString first. Did not remove the owner-only permission requirement or skip the test.
4. Focused configuration/helper tests passed, including weak-key rejection. Final combined clean verify then passed all 89 cases. No algorithm, issuer, permission or database-role bypass was introduced to make tests pass.
5. Final API review made the 201 receipt schema and per-operation application/problem+json errors explicit, including query 409 and verification 429. Strengthened the generated-document assertions and reran normal clean verify: all 63 cases passed. This run intentionally omitted PostgreSQL, leaving its 26-case evidence in the earlier combined log rather than claiming another reference execution.

## Remaining boundaries

Executed PostgreSQL version is 16.3, not the configured Compose/CI 17.6 image. Fresh Compose initialization (including psql-only password environment script), remote CI and external issuer integration were not executed. Tests exercised the actual role-definition SQL and Flyway grants with separate random passwords. Existing volumes/roles are not silently upgraded or replaced. Reusing meaningful/shared databases is outside the fixture authorization.

Dependency-vulnerability scanning, load/SLO evidence, distributed quotas, production KMS, migration-job operational isolation, multi-key rollover/revocation, archive/redaction, exports and compliance remain later work. The app still has startup migration credentials when Flyway runs in-process; runtime-pool restrictions alone do not protect against complete process compromise. H2 has no equivalent role-hardening claim. Swagger's protected HTML/assets require a client/browser that sends Bearer headers on those requests.

M1 522fac7, M2 45229d1 and M3 a4a7780 are observed actual prior commits. M4 changes are uncommitted at delivery; no commit, signature, remote publication or access grant was fabricated. AI and unsigned attestation records are updated. Final review covered 94 deliverable files: no missing local Markdown links, trailing whitespace or targeted private-key/AWS-key/JWT-pattern findings. POM XML parsed and git diff --check passed; applied V1/V2 migrations were unchanged. Final normal XML reports independently totaled 63 cases with no failures/errors/skips; the earlier successful reference log confirms all 26 PostgreSQL cases and normal cluster stop. Raw logs/local keys/test credentials remain ignored under .tools and target. These are limited local checks, not a comprehensive secret/advisory scan or remote CI result.
