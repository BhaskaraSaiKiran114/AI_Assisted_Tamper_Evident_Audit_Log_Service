# Milestone 3 validation and delivery evidence

Date: 2026-10-28 (Asia/Calcutta). Status: M3 complete, human acceptance pending. User authorization: "now implement Milestone 3". Planning progress: 3/8 milestones (37.5%); not an accuracy or reviewer score.

## Scope delivered

- GET /audit/events: parameterized combined actor/resource/type/occurrence-time filters, input bounds and ascending sequence keyset pagination.
- HMAC-signed cursors bound to filters/limit and a fixed upper sequence/head hash; invalid cursors return 400, changed/missing anchors 409.
- GET /audit/events/{eventId}: validated lookup, stored-row integrity checks and authenticated restoration of protected payload values; no raw keys exposed.
- Required GET /audit/verify: full streaming chain traversal, exact digest/column/link/sequence/head checks, first inconsistency and violation type. Missing records have null event IDs; operational failures are safe 503 responses.
- ReadSnapshot uses PostgreSQL REPEATABLE_READ / H2 SERIALIZABLE in separate read-only transactions, preserving append isolation and preventing a caller's weaker transaction from changing read semantics.
- No schema migration was required; V1/V2 were not edited. No archive/redaction behavior, JWT policy or external checkpoint was implemented ahead of its milestone. Runtime business HTTP remains denied until M4.

## Verification environment and command

Temurin 21.0.12.1+1; Maven 3.9.16; Boot 4.1.0; H2 2.4.240; PostgreSQL 16.3 from installed binaries. Used populated project-local cache offline. The corrected existing runner created a new GUID-named cluster under ignored .tools on loopback 55432 with a generated SCRAM password. It did not use or control the existing PostgreSQL service.

```powershell
$env:JAVA_HOME = '<project>\.tools\jdk21\jdk-21.0.12.1+1'
$env:MAVEN_USER_HOME = '<project>\.tools\maven-user-home'
.\scripts\verify-postgres.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\16\bin' `
    -MavenRepository '.tools/repository' -Offline
```

The runner invokes `mvnw.cmd -Ppostgres-it clean verify` with cache/offline arguments. The dedicated database is still named audit_m2_test to retain the existing explicit destructive-test guard. Its inherited fixture clears only that isolated store. The runner stopped its own cluster after completion, as confirmed by server stopped in the log. Raw log: ignored .tools/m3-postgres-verify.log; test reports: ignored target/surefire-reports and target/failsafe-reports.

| Check | Result |
| --- | --- |
| Full clean verify | BUILD SUCCESS, exit 0; 50.542 seconds, completed 02:11:49 Asia/Calcutta |
| Toolchain / compiler | Java 21 and Maven enforcement passed; release 21 |
| Checkstyle | Zero violations across main/test sources |
| Normal Surefire suite | 47 tests: prior 27 + 2 filter + 2 cursor + 16 H2 read/verification cases |
| PostgreSQL Failsafe suite | 24 tests: 8 append + 16 read/verification cases |
| Combined result | 71 tests, 0 failures, 0 errors, 0 skips |
| Migration and package | Existing V1/V2 validated/applied on fresh stores; executable Boot JAR packaged |
| Process cleanup | Temporary PostgreSQL cluster stopped normally; no existing service operation performed |

## Coverage exercised on both H2 and PostgreSQL

The shared 16-case read contract covers empty/nonempty chains, lookup and decrypted synthetic payload, each filter and combinations, time boundaries, injection-like filter values, sparse filtered pagination across later appends, cursor/filter binding, anchor changes, changed canonical content, searchable/duplicate payload column changes, timestamp changes, bad predecessor links, changed content with recomputed own hash breaking its successor, internal/tail deletion, wrong/missing/underreported head, malformed rehashed JSON, unknown committed schema version, missing-key availability separate from integrity, and standalone HTTP status/error behavior.

A latch-controlled test pauses the verifier after its head read but before row streaming, commits an append on another connection, then resumes. The result must remain intact for the old snapshot, and a subsequent verification sees the new record. This test first failed on H2 REPEATABLE_READ and passed with the corrected per-database snapshot selection; it also passed on real PostgreSQL.

Standalone MVC tests are not an authentication bypass in the running application. Foundation tests continue to assert business queries and verification are denied by the real security chain. Runtime JWT permissions remain M4.

## Iterations and repairs

1. Initial compile failed on an AssertJ wildcard-map test assertion. Changed the assertion to compare the selected value; production code compiled unchanged.
2. First executing H2 suite ran 46 cases with one snapshot-concurrency failure. H2 REPEATABLE_READ admitted a phantom row after the head observation, creating a false integrity break. Consulted official database documentation and implemented ReadSnapshot: H2 SERIALIZABLE, PostgreSQL REPEATABLE_READ, read-only separate transactions, unchanged append isolation. Did not weaken or skip the test.
3. Added an unknown-version case (16 read tests) and strengthened pagination to span nonmatching event gaps. A subsequent H2 test run passed 47 cases; final combined clean verify passed all 71.
4. Review removed an arbitrary whole-document stored-JSON byte cap: legitimate ciphertext/decimal expansion may exceed the original request size. Request/cursor bounds remain enforced separately; stored decoding retains depth/string/number constraints. The final combined run includes this refinement.

Rationale/source links are in ADR-005 and the query/verification contract. Public documentation lookups used only generic H2/PostgreSQL questions; no confidential assignment or payload was uploaded.

## Remaining boundaries

PostgreSQL version executed is 16.3; configured CI/Compose 17.6 and remote CI are unexecuted. Dependency-security scan, benchmark/SLO, restricted runtime DB roles, JWT/OpenAPI enablement, archived-record behavior, audited redaction, external checkpoints, exports and compliance reporting remain later work. Verification checks ciphertext commitments independently of decryption-key availability; it cannot prove upstream completeness or defeat coherent whole-database rewriting without external evidence.

Query pages are bounded but may perform one key lookup per returned event. Full verification is O(n), memory depends on stored record size, H2 may internally materialize results, and transaction timeout settings are not hard CPU/wall-clock SLAs. Per-request snapshots/cursors do not promise immutable historical payloads across requests after future lifecycle changes. Protected plaintext returned to authorized readers differs from the ciphertext representation committed by recordHash.

M1 commit 522fac7 and M2 commit 45229d1 were observed in actual history. M3 changes are uncommitted at delivery; no commit, publication, access grant or candidate signature was fabricated. Attestation and AI records are updated; human review remains pending. Final review covered 79 deliverable files: no missing local Markdown links, trailing whitespace or targeted private-key/AWS-key/JWT-pattern findings. git diff --check passed; .tools/.env/target remain ignored; no existing migration changed. JUnit XML reports independently totaled 71 tests with no failures/errors/skips. These limited checks are not a comprehensive secret/security scan.
