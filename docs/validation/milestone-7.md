# Milestone 7 validation - bounded account-access reporting

Date: 2026-10-28, Asia/Calcutta. User authorization: "now implement Milestone 7". M8 is not started; human output acceptance and personal attestation remain pending.

## Scope and pre-code interpretation

The M1 Scenario C clarification was refined in access-report.md before report code: CLIENT_ACCOUNT READ/UPDATE/EXPORT with a plaintext top-level payload.outcome SUCCESS/DENIED/FAILURE, mandatory bounded occurrence-time range, optional exact account/actor/action/outcome filters, metadata-only projection and explicit unsupported/protected-outcome exclusions. These are prototype assumptions, not actual legal obligations or certification.

Implemented GET /audit/compliance/access-report with independent COMPLIANCE permission and LIFECYCLE for archive opt-in; versioned bounded pagination; a separate report cursor purpose/context; streamed canonical candidate checks; no key lookup/decryption or payload bypass; scan/window/concurrency bounds with whole-page failure and permit release. Existing event encoding, migrations, privileges and ordinary query cursor v2 remain unchanged. ADR-009 and current governance/API/requirements records document the boundary.

## Actual execution

Windows, portable JDK 21.0.12.1+1, Maven wrapper 3.9.16, Spring Boot 4.1.0, H2 and local PostgreSQL 16.3. No external source searches, meaningful client data or existing services were required.

```powershell
$env:JAVA_HOME = 'C:\Tamper_Evident_Audit_Log_Service\.tools\jdk21\jdk-21.0.12.1+1'
$env:MAVEN_USER_HOME = 'C:\Tamper_Evident_Audit_Log_Service\.tools\maven-user-home'
& .\scripts\verify-postgres.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\16\bin' -MavenRepository '.tools/repository' -Offline
```

Runner executed Java 21 offline -Ppostgres-it clean verify in a new GUID-named loopback cluster, supplied guarded disposable-database/role consent and stopped its cluster normally. Raw output: ignored `.tools/m7-postgres-verify.log`.

**BUILD SUCCESS at 10:28:06 +05:30; duration 55.832 seconds.**

| Gate | Actual result |
| --- | --- |
| Surefire unit/H2/security | 132 passed |
| PostgreSQL Failsafe | 74 passed |
| Packaged offline-verifier Failsafe | 1 passed |
| Combined | 207, zero failures/errors/skips |
| Checkstyle | Zero violations |
| Package | Executable Boot application and independent verifier JAR |
| Temporary PostgreSQL | Normal stop recorded |

M7 adds 12 shared report cases per datastore, five parser/configuration cases and five real-JWT HTTP cases per datastore. PostgreSQL HTTP uses the actual restricted Spring runtime pool, with guarded separate administrator connections only for fixture cleanup. All previous ingestion/query/verification/lifecycle/export/security/artifact tests remain in this execution. Normal verify selects 132 Surefire plus the same one independent CLI case; the combined execution includes every such case, and repeated runs are not added to totals.

## Coverage and iterations

Shared cases establish occurrence-time lower inclusion/upper exclusion independently of ingestion age; all recognized actions/outcomes; exact combined actor/account/action/outcome filters including SQL metacharacters; unsupported outcome counts and explicit resource/event scope; sparse signed pagination with later appends excluded; archive/redaction/key independence and no report wrapped-key lookup; protected outcomes excluded rather than decrypted; scan-limit atomic failure/capacity recovery; corrupted candidate/archive proof safe 503; cursor binding to account/actor/action/outcome/range/limit/archive mode plus ordinary-query cross-replay rejection; missing-anchor 409; oversized-window 400; concurrent snapshot/429 behavior; empty genesis with no claimed access.

HTTP contracts prove missing/other permissions fail, COMPLIANCE does not grant generic read/export/docs, report JSON has exactly eight approved entry fields and no payload/key leakage or write side effect, archive opt-in adds LIFECYCLE, bounds/scan errors are safe, and protected OpenAPI describes report permission/errors. Parser tests reject unknown/repeated/unbounded/vocabulary/precision inputs, normalize equivalent UTC offsets, exclude cursor text from context and fail invalid resource configurations.

Initial compile/package passed at 10:21:28 in 9.245 seconds. First focused run had 21 tests with one failure: a no-key-read spy assertion included fixture setup's legitimate lifecycle redaction key lookups. Scoped invocation history to the report call, preserving the never-read assertion, and added empty-genesis coverage. No production validation was weakened. Final combined run passed all 207 tests with the correction. There were no source/schema migration changes after this gate; later edits are documentation only.

## Scope limits and ownership

Final local hygiene review passed: XML totals independently confirm 132 Surefire + 75 Failsafe tests, all zero failures/errors/skips. POM XML parses, local Markdown links resolve, and 144 deliverable files were inventoried without trailing whitespace or limited private-key/AWS-key/JWT-pattern matches. git diff --check passed; .tools/.env/target remain ignored and no database migration changed. This is basic hygiene, not a comprehensive security audit.

This is a bounded producer-record inspection report. Unsupported/protected outcomes are excluded and counted; no success or real-world identity is inferred. Account/actor identifiers remain sensitive exposed metadata and outside selective field-key erasure. Per-request scan/exclusion counts include lookahead and can repeat across pages; they are not period totals. Query predicates can hide omitted/corrupted nonmatching rows; no full-store/lifecycle check, upstream completeness, legal hold, tenant enforcement, signed report delivery or regulatory certification is claimed. Use separate verification/export operations for their stated evidence claims.

PostgreSQL 17.6/Compose/remote CI, dependency advisory scan, performance/load, independent human review and M8 readiness are not claimed executed. Actual prior M6 commit 86a1493 observed; M7 is uncommitted at delivery. No fabricated acceptance/signature/commit, external publication, reviewer access grant or submission. Planning progress **7/8 = 87.5%** is not a functional accuracy or reviewer score.
