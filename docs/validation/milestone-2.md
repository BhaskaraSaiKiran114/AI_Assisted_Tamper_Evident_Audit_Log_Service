# Milestone 2 validation and delivery evidence

Date: 2026-10-28 (Asia/Calcutta). Status: M2 complete, human acceptance pending. Authorization: user instructed "now implement Milestone 2". M1+M2 = 2/8 milestones (25% planning progress, not an accuracy/reviewer score).

## Implemented scope

- POST ingestion adapter and application service with strict bounded parsing, validation, server identity/sequence/ingestion timestamp and caller occurrence-time handling.
- Versioned deterministic JSON and SHA-256 framing, exact canonical-byte storage, genesis linkage and independent hash vectors.
- V2 event/key migration and query-ready indexes; singleton database row lock with transactional event/key/head persistence.
- AES-256-GCM per-field payload protection with event/path-associated data; per-field keys wrapped by an external master key (ephemeral H2 convenience only).
- No update/delete endpoint; application security still denies all business HTTP until M4. Standalone controller tests are adapter tests, not a permissive running application.
- Shared H2/PostgreSQL append contract, opt-in postgres-it profile, isolated PostgreSQL runner and configured CI database service.

## Executed checks

Verification JDK: Temurin 21.0.12.1+1, Maven 3.9.16, Spring Boot 4.1.0. Cached dependencies used offline. Real database: local PostgreSQL 16.3 binaries, fresh cluster under ignored .tools, loopback 55432, generated SCRAM password. Existing PostgreSQL service was not used for tests and remained running; no application database was accessed. The successful temporary cluster was stopped after Maven completion.

```powershell
$env:JAVA_HOME = '<project>\.tools\jdk21\jdk-21.0.12.1+1'
$env:MAVEN_USER_HOME = '<project>\.tools\maven-user-home'
.\scripts\verify-postgres.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\16\bin' `
    -MavenRepository '.tools/repository' -Offline
```

The runner executes `mvnw.cmd --batch-mode --no-transfer-progress -Ppostgres-it clean verify` with local cache/offline arguments and explicit disposable-database consent. PostgresAppendIT rejects absent consent or any database name other than audit_m2_test before creating a Spring database connection. Do not point it at meaningful data.

| Check | Result |
| --- | --- |
| Combined reference clean verify | BUILD SUCCESS; exit 0, 24.049 seconds, completed 01:35:43 Asia/Calcutta; 27 unit/H2 + 8 PostgreSQL tests |
| Final normal clean verify | BUILD SUCCESS; exit 0, 31.790 seconds, completed 01:40:36 Asia/Calcutta; 27 unit/H2 cases after protected-path validation refinement; reference profile intentionally not enabled in this follow-up |
| Java/Maven enforcement | Passed, compiler release 21 |
| Checkstyle | Zero violations |
| CanonicalJsonTest | 3 passing tests: exact encoding, independent framing digest, invalid Unicode/number limits |
| EventParserTest | 11 passing cases, including parameterized invalid payloads |
| PayloadProtectorTest | 2 passing tests: encryption/decryption, randomization, event/path binding and configuration guards |
| FoundationIntegrationTest | 3 passing tests; public minimal health and denied business/docs access preserved |
| IngestionIntegrationTest | 8 passing H2 contract tests |
| PostgresAppendIT | 8 passing real PostgreSQL contract tests |
| Distinct cases exercised | 35 tests, 0 failures, 0 errors, 0 skips (27 Surefire + 8 Failsafe in combined run); final follow-up revalidated all 27 normal cases |
| Flyway | V1 and V2 applied on fresh H2 and PostgreSQL stores |
| Packaging | Executable Spring Boot JAR produced |
| Test cluster cleanup | Successful run logged server stopped; temporary artifacts remain ignored |

The shared eight-test append contract validates a fixed full-envelope golden digest, first/successor commits and encrypted storage, caller time, 32 concurrent appends over 8 worker threads with no gaps/forks, outer rollback after head advance, injected key-insert failure after event insertion, invalid-input no-write behavior, and controller 201/400/413 plus absence of PUT/DELETE routes. These tests use dedicated disposable stores and perform intentional destructive setup only there.

## Iterations and failures

1. First compile rejected an int argument to Spring's ProblemDetail.forStatusAndDetail. Changed it to HttpStatusCode.valueOf; no suppression.
2. First H2 suite passed 26 tests. Added an independently calculated full-envelope golden vector, then final combined verification passed 27+8 cases.
3. Initial Windows pg_ctl invocation inherited process-output handles and stalled after server start. Interrupted the attempt and changed the runner to start pg_ctl with explicit hidden process/output handles and bounded wait. A subsequent cleanup found the original temporary postmaster already gone with a stale PID file.
4. During diagnosis, a pg_ctl process was misidentified as belonging to the test run. A termination attempt was denied; no process was stopped by that attempt. The existing PostgreSQL service was confirmed running. Subsequent execution tracks only process handles created by the runner and cluster directories unique to each run; no broad process termination is used.
5. Corrected runner initialized a new cluster, ran all tests successfully and stopped that cluster. Old interrupted-run artifacts are ignored; no recursive deletion or existing-service operation was performed.
6. Final review tightened invalid JSON-pointer escape detection across newline characters and extended the existing configuration guard test. Reran normal clean verify successfully; database persistence/transaction code did not change, so the previous PostgreSQL contract evidence remains applicable.

## Boundaries and remaining gates

Local reference evidence is PostgreSQL 16.3, not the configured Compose/CI 17.6 image. Remote CI, PostgreSQL 17.6 execution, dependency-vulnerability scanning, production database grants, performance, JWT permissions, query/full verification, redaction/key erasure and exports are not claimed complete. H2/Flyway verified-version and Mockito agent warnings from M1 remain nonfatal and visible. Encryption protects configured payload values, not all event metadata, JVM memory or backups; no production KMS or complete erasure claim is made.

Final local document-link/whitespace, targeted secret-pattern and ignored-artifact checks passed before delivery. POM XML parsed successfully. An optional YAML parser was unavailable in bundled Python; workflow configuration was reviewed as text and remote CI is explicitly unexecuted. Raw logs/reports are local under .tools and target; they are not committed and may include synthetic test details. Human personal attestation remains unsigned. No commit or external publication was performed by this milestone.
