# Milestone 6 validation - signed export and independent verification

Date: 2026-10-28, Asia/Calcutta. User authorization: "now implement Milestone 6". M7 is not started. Human output acceptance and personal attestation remain pending.

## Delivered scope

- GET /audit/exports with strict actor/resource selection, AND semantics, explicit archive opt-in and separate EXPORT/LIFECYCLE permissions.
- Complete signed ZIP: selected original protected canonical bytes, minimal whole-snapshot bridging headers, ordered manifest committing membership, snapshot, filters, status/body digest and key fingerprint.
- A separate configured RSA signing key with no random fallback; missing key returns 503, invalid/weak key fails startup.
- Bounded snapshot/whole-chain checks, lifecycle validation, source-corruption rejection, capacity/proof/selection/byte limits and atomic failure without partial output.
- A dependency-free Java 21 classifier verifier JAR: pinned external key, exact signature/body hash, selected content rehashing, envelope/header binding, sequence/link/head/filter/membership checks and bounded archive parsing.
- Generated OpenAPI contract, export guide, ADR-008 and updated governance/requirements/evidence. No migration, broad privilege extension, private/field key export or M7 report implementation.

## Executed gates

Windows, portable JDK 21.0.12.1+1, Maven wrapper 3.9.16, Boot 4.1.0, H2 and PostgreSQL 16.3. Existing services/useful data were not touched; tests use synthetic data and a new loopback cluster.

```powershell
$env:JAVA_HOME = 'C:\Tamper_Evident_Audit_Log_Service\.tools\jdk21\jdk-21.0.12.1+1'
$env:MAVEN_USER_HOME = 'C:\Tamper_Evident_Audit_Log_Service\.tools\maven-user-home'
& .\scripts\verify-postgres.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\16\bin' -MavenRepository '.tools/repository' -Offline
.\mvnw.cmd '-Dmaven.repo.local=.tools/repository' --offline --batch-mode --no-transfer-progress clean verify
```

The combined offline -Ppostgres-it clean verify passed at **10:05:13 +05:30**, duration **52.687 seconds**. Ignored raw output: `.tools/m6-postgres-verify.log`; its separate temporary cluster stopped normally.

| Gate | Actual combined result |
| --- | --- |
| Surefire unit/H2/security | 110 passed |
| PostgreSQL Failsafe | 57 passed |
| Packaged offline CLI Failsafe | 1 passed |
| Total | 168, zero failures/errors/skips |
| Checkstyle | Zero violations |
| Compilation/package | BUILD SUCCESS; Boot application and independent verifier JAR |

M6 adds 11 shared export cases on each database, nine portable format/key/parser tests and five real-JWT HTTP cases on each database (PostgreSQL HTTP uses the actual restricted runtime pool). One packaged artifact test starts the actual verifier in separate Java processes, accepts its trusted key, rejects a different key/malformed bytes, and asserts the artifact contains only the portable verifier classes. It runs after packaging and needs no database. The normal Maven default now includes that test; postgres-it includes it once alongside database tests. Counts across repeated runs must not be added together.

## Coverage and iterations

Shared contracts prove sparse selection with unrelated-payload minimization and exact stored bytes; empty genesis/no-match membership; combined resource/actor and escaped Unicode identifiers; archived/redacted export preserving ciphertext; key-independent export without inventing erasure status; corruption of unselected source records; missing/head/lifecycle evidence; all-or-nothing proof/selection/byte caps; permit release; concurrent append snapshot and 429 saturation.

Portable tests reject wrong trust/signature, altered body/manifest, unsupported versions/algorithm, missing/reordered headers, wrong head/membership/filter, absent/extra/truncated/oversized ZIP entries and missing/malformed/weak signing configuration. Adversarial tests also re-sign malformed proof using fixture keys to establish structural/hash checks beyond signature rejection. HTTP contracts cover permission isolation, no-store binary attachment, no write side effect, archive opt-in, strict filters, safe errors and protected generated schemas. Existing runtime mutation-denial suites remain active.

Initial package compiled successfully. First focused 19-case run had one incorrect expected exception type in a parser test; corrected to the existing InvalidEventException contract (no product validation weakened). Initial full normal run then passed 110 tests at 09:53:00, duration reported 01:02 min. The combined 168-case run passed. After enabling the packaged CLI test in normal verify, all 110 normal tests and CLI assertions passed but JUnit temporary-directory cleanup failed with Windows AccessDeniedException outside the workspace; that run was correctly a failed gate. The same final normal command was rerun outside the sandbox to permit cleanup. Final normal clean verify passed at **10:08:31 +05:30**, **34.335 seconds**: 110 Surefire plus one CLI Failsafe, zero failures/errors/skips, zero style violations and both artifacts packaged. Raw output: `.tools/m6-final-normal-verify.log`. Only the default Failsafe selection changed after the combined gate; postgres-it overrides it and its code/database tests were unchanged.

## Trust and remaining boundaries

Final local review passed: final normal XML reports independently total 110 Surefire and one Failsafe test with zero failures/errors/skips; combined raw-log summaries confirm 110 + 58 and normal cluster stop. POM XML parses, all local Markdown links resolve, and 131 deliverable files were inventoried with no trailing whitespace or limited private-key/AWS-key/JWT-pattern matches. git diff --check passed; .tools/.env/target remain ignored; no database migration changed. These checks are basic hygiene, not a comprehensive security audit.

Selected exact-byte hashes are independently recomputed; unrelated hidden-content headers and lifecycle flags are signed assertions. No proof of complete filter selection, upstream truth, all hidden-content hashes, physical erasure or regulatory compliance is claimed. Trust the signing identity and verifier distribution separately. Unprotected selected payload fields/metadata and proof structure remain disclosed. Prior exports/old wrapped-key material and WAL/backups/replicas/MVCC/in-flight reads limit erasure. No KMS/HSM, revocation, operated external checkpoints, range-proof upgrade, legal hold or automatic export job.

Normal verification and the combined gate are local evidence, not remote CI. PostgreSQL 17.6/Compose, external advisory scan, performance/load and human acceptance remain unexecuted. Prior actual M5 commit e3e5107 observed; M6 uncommitted at delivery. No fabricated signature, commit, reviewer access, publication or submission. Progress is **6/8 = 75%**, not a functional accuracy/reviewer score.
