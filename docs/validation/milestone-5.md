# Milestone 5 validation - retention and redaction

Date: 2026-10-28, Asia/Calcutta. User authorization: "now implement Milestone 5". M6 has not started. Human acceptance and personal attestation remain pending.

## Delivered scope

- Manual bounded archival by server ingestion time, configurable window/policy/batch, strict cutoff and administrative-record exclusion.
- Selective erasure of previously protected payload fields, explicit confirmation/approval reference, immutable administrative evidence and readable redaction markers.
- Atomic action/event/marker/key transactions under the append lock; operation IDs bind actor and normalized intent, exact replay returns the original receipt, conflicts return 409.
- Default archive exclusion; includeArchived=true requires READ plus LIFECYCLE. Cursor v2 binds mode and rejects old v1 cursors.
- V4 lifecycle tables and PostgreSQL V5 restricted SECURITY DEFINER erasure function. Prior V1/V2/V3 migrations remain unchanged. No direct runtime DELETE grant.
- Full verification cross-checks committed lifecycle proof and detects missing/changed markers, missing actions and restored keys. Original bytes, ciphertext and hashes stay unchanged.
- API schemas, lifecycle guide, ADR-007, governance, requirements traceability, AI log and unsigned attestation updated.

## Actual execution

Environment: Windows, bundled portable JDK 21.0.12.1+1, Maven wrapper 3.9.16, Spring Boot 4.1.0, H2 and installed PostgreSQL 16.3 binaries. Existing PostgreSQL services and useful data were not used or changed.

```powershell
$env:JAVA_HOME = 'C:\Tamper_Evident_Audit_Log_Service\.tools\jdk21\jdk-21.0.12.1+1'
$env:MAVEN_USER_HOME = 'C:\Tamper_Evident_Audit_Log_Service\.tools\maven-user-home'
& .\scripts\verify-postgres.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\16\bin' -MavenRepository '.tools/repository' -Offline
```

The runner executed offline Java 21 `-Ppostgres-it clean verify` against a new GUID-named loopback cluster, supplied guarded test consent and stopped its own cluster in cleanup. Local raw output: ignored `.tools/m5-postgres-verify.log`. Final result at **09:18:01 +05:30**, duration **47.458 seconds**:

| Gate | Actual result |
| --- | --- |
| Surefire unit/H2/security | 85 passed |
| PostgreSQL Failsafe | 41 passed |
| Combined | 126 tests; zero failures/errors/skips |
| Checkstyle | Zero violations |
| Compilation/package | BUILD SUCCESS; executable Boot JAR |
| Temporary PostgreSQL | Normal server stop recorded |

Normal suite adds 13 shared lifecycle cases, three parser cases and six real-JWT lifecycle cases to the prior 63. PostgreSQL adds the same 13-case lifecycle contract and two actual restricted-pool cases to the prior 26.

Evidence includes cutoff equality/server-age versus occurrence-age; batches/hasMore; replay/conflict and eight concurrent duplicate commands; selective/repeated erasure; mixed valid/invalid path atomicity; unexplained missing-key rejection; rollback after actual key deletion and archive insertion; archive marker tampering; restored keys/deleted proofs; archived-target redaction; verification snapshot during concurrent erasure; permission/namespace/request bounds; generated schema and conditional archive access. Restricted-pool tests reject unaudited function calls and direct deletion while authenticated audited erasure succeeds.

Iterations: baseline wiring passed the previous 63 normal tests. Expanded compilation initially failed Checkstyle on an unused test import; removed it. Expanded normal suite passed 85. Final combined run includes the final lifecycle request-body schema annotations. Two patch attempts rejected mismatched context before applying and were reissued with correct context; no bypass or weakened assertion was introduced.

## Review and limits

Final local review passed: test-report XML totals independently confirm 85 + 41 cases with zero failures/errors/skips; POM XML parses; 114 deliverable files were inventoried; local Markdown links resolve; no trailing whitespace or limited private-key/AWS-key/JWT-pattern matches were found; git diff --check passed. Git confirms .tools/.env/target are ignored and no existing database migration changed. These checks are basic hygiene, not a comprehensive security audit. No real key, JWT, assignment copy or client payload belongs in the deliverable.

Erasure covers the active transactional store only. WAL, backups, replicas, earlier MVCC snapshots/in-flight reads, caches and previous disclosures can retain recoverable material. Operator approval references are supplied claims, not independently verified approvals. No physical purge, scheduler, legal hold, tenant policy, production KMS or legal certification is provided. Compromised runtime credentials can manufacture future inserts/evidence within their capabilities. Lifecycle proof lookups add cost; performance measurement remains M8.

PostgreSQL 17.6/Compose/remote CI, dependency advisory scan, load/performance tests and independent human review are not claimed executed. M6 export, M7 reporting and M8 readiness remain unstarted. Actual prior M4 commit: 7039dbb. M5 is uncommitted at delivery; no signature, remote publication or reviewer access grant performed. Planning progress is **5/8 = 62.5%**, not an accuracy or acceptance score.
