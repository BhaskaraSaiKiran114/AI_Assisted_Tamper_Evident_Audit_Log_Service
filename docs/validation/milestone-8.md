# Milestone 8 validation - final prototype readiness

2026-10-28, Asia/Calcutta. User authorized "now implement Milestone 8". All eight engineering deliverables are delivered; human acceptance, attestation identity/signature, remote repository privacy/reviewer access and submission remain pending. Implementation progress 8/8=100% is not an accuracy/reviewer/production-certification score.

## Delivered work

- Opt-in readiness profile and Windows runner switch for real HTTP A/B/C, SQL tamper evidence, actual offline verification, a 1000-request performance baseline and fresh executable-JAR startup/shutdown.
- Offline verifier file reads bounded even if an input grows after size inspection; actual CLI rejects oversized key/bundle files.
- Completed advisory scan, compatible dependency remediation retaining Boot 4.1.0, protected Swagger asset-version alignment and scanner 13.0.0 upgrade for complete import.
- Walkthrough, focused security review, measured performance, sanitized security/metric/artifact records, final engineering summary and optional repeatable deliverable-hygiene script.
- Updated requirements/architecture, traceability, milestone state, AI usage and unsigned personal-attestation progress. No migration/schema/hash-format change, meaningful data mutation, remote publication or automatic personal sign-off.

## Actual final gates

Windows 11 amd64; Intel i7-1260P/16 logical processors, approximately 16.8 GB reported physical memory; portable JDK 21.0.12.1+1; Maven wrapper 3.9.16. PostgreSQL binaries/server 16.3, actual audit_runtime pool with separate migrator and guarded admin fixtures. Patched runtime versions are recorded in performance JSON and security review; Boot is 4.1.0.

```powershell
$env:JAVA_HOME = 'C:\Tamper_Evident_Audit_Log_Service\.tools\jdk21\jdk-21.0.12.1+1'
$env:MAVEN_USER_HOME = 'C:\Tamper_Evident_Audit_Log_Service\.tools\maven-user-home'
.\scripts\verify-postgres.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\16\bin' -MavenRepository '.tools/repository' -Offline -Readiness
```

Final patched `-Ppostgres-it,readiness clean verify`: **BUILD SUCCESS, 15:18:10 +05:30, 01:58 min**, raw log ignored `.tools/m8-final-fanout.log`.

| Gate | Actual final result |
| --- | --- |
| Surefire unit/H2/security | 132 passed |
| Reference PostgreSQL Failsafe | 74 passed |
| Portable CLI artifact Failsafe | 1 passed |
| Opt-in readiness Failsafe | 3 passed (live A/B/C; measurement; separate executable-JAR H2 process) |
| Combined | 210; zero failures/errors/skips |
| Checkstyle | Zero violations |
| Runtime/privileges | Actual restricted pool; denied mutation, guarded synthetic administrator tamper detected |
| Fresh setup | New GUID PostgreSQL cluster/role bootstrap/migrations; separate packaged H2 migration/startup |
| Cleanup | Temporary cluster and child application stopped normally |

Ordinary verify selects 132 + one CLI case (133); postgres-it selects 132 + 75 (207); postgres-it,readiness adds three (210). Do not add overlapping reruns. Reference/CI image 17.6 and Docker initialization are not claimed tested by local 16.3/H2 execution.

## Advisory audit and repairs

Initial Maven network access was sandbox-denied and retried with approval. The anonymous NVD API made repeated request failures and incomplete progress; interruption left a cache lock. The first feed attempt waited on that lock rather than importing a feed. Only this task's exact matching Dependency-Check process was stopped after scoped process identification. A separate supported mirror cache completed import and exposed CVSS >=7 findings. No partial import was treated as success.

Review against primary advisories and Maven Central patch metadata led to explicit compatible overrides: Framework 7.0.8 -> 7.0.10; Security 7.1.0 -> 7.1.2; Tomcat 11.0.22 -> 11.0.27; JDBC 42.7.11 -> 42.7.14; Jackson 2 BOM 2.21.4 -> 2.21.8; Log4j2 2.25.4 -> 2.25.5; Swagger UI 5.32.11 -> 5.34.0. Boot remains 4.1.0 and Springdoc 3.1.0. This patches component ranges without claiming all flagged features were reachable.

The interim patched 12.1.8 scan passed its threshold, but original import logs contained two skipped Mozilla CVEs due to reference URLs exceeding a 1000-character database column. Upgrade to Dependency-Check 13.0.0 and a fresh full mirror import resolved that coverage gap. Its **final audit passed at 15:06:14 +05:30, 03:30 min**, with **59 dependency records, zero reported vulnerable records and no import errors logged**. Raw `.tools/m8-security-v13.log`; sanitized [security metadata](milestone-8-security.json). NVD cache modified 2026-10-27T16:17:33Z, checked 2026-10-28T09:33:21Z. NVD/CPE and RetireJS ran; OSS Index automatically disabled without credentials. Default compile/runtime scope, mirror freshness, unscanned server/JDK binaries and future advisories limit coverage. CVSS>=7 and failOnError=true remain enforced; no project suppression was added.

```powershell
.\mvnw.cmd '-Dmaven.repo.local=.tools/repository' --batch-mode --no-transfer-progress -Psecurity-audit org.owasp:dependency-check-maven:13.0.0:check '-DnvdDatafeedUrl=https://dependency-check.github.io/DependencyCheck_Builder/nvd_cache/nvdcve-{0}.json.gz'
```

The plugin requires Maven online mode, even when explicitly reusing cached data; an attempted offline invocation was rejected and corrected. HTTPS trust used the Windows trust store where needed; TLS validation was never disabled. Cached snapshots were isolated instead of deleting useful databases or bypassing failures.

## Functional/performance evidence and iterations

The real HTTP walkthrough proves secured ingest/query, denied API/SQL mutation, canonical-byte preservation through acknowledged/replayed redaction, archival with controlled aged fixture, protected archived access, COMPLIANCE-only metadata reporting, signed actual bundle accepted by the separate verifier JAR, intact chain then administrator-only synthetic searchable-field corruption detected as INDEX_FIELD_MISMATCH, and corrupted-source export refusal. The packaged H2 child also proves no export signing fallback. Roles, keys, clock fixtures and data are synthetic. No system clock or pre-existing event timestamp was changed.

Final 1000-event measurement: four append clients, 218-byte request/one protected field, 50 warmups then reset, 5.357 s batch, **186.665 appends/s**, p50 20.815 ms, p95 26.570 ms, p99 36.099 ms. Query page 50 p50 61.360 ms; full verification 1000 p50 240.300 ms; report page 50 p50 22.389 ms; protected export 1000 p50 413.095 ms. The additional 100-record archive command took 62.726 ms; one field erasure 22.508 ms. The archived page 50 median was 1128.423 ms; post-lifecycle verification of 1102 records median 254.132 ms and export of 1000 selected records median 422.588 ms. See [performance discussion](../performance.md) and [raw sanitized metrics](milestone-8-performance.json). No SLO or patch-performance attribution is inferred. All final raw readiness artifacts are ignored under `.tools/m8-readiness-final`; initial pre-patch outputs remain `.tools/m8-readiness`.

Earlier evidence: initial unpatched readiness passed 210 at 10:58:27 (01:10 min); bounded-reader regression passed 207 at 11:13:04 (01:27 min). The first patched run failed one of 132 normal tests because authorized Swagger HTML returned 404 with Springdoc's stale default WebJar version. Filtered YAML was aligned to the Maven property; the original 14 security tests then passed unchanged at 14:55:26 (39.348 s), followed by the final patched 210-case gate above. No business/source validation assertion was weakened.

An intermediate metadata repackage after the audit-tool update passed at 15:07:54 (6.046 s), explicitly skipping tests; an initially unquoted PowerShell Maven property was corrected. The remaining planned lifecycle-cost measurement was then added: 100 aged fixtures, one field erasure, one archive batch of 100 and three samples each of archived-page/verification/export work. The final 15:18:10 readiness gate rebuilt both artifacts under the final POM and passed all 210 checks. Only documentation/hygiene review followed that gate. Hash comparison proved every BOOT-INF runtime entry and portable class entry unchanged from the earlier patched build; the added exercise is test-only. Final sizes/SHA-256 are in [artifact fingerprints](milestone-8-artifacts.json), identifying the delivered local build rather than signed provenance. No test pass is claimed from the earlier skip-tests package command.

## Ownership and residual limits

Final consistency/hygiene checks passed: XML totals independently confirm 132 + 78 = 210 with zero failures/errors/skips; final advisory log has BUILD SUCCESS and no ERROR lines; POM and sanitized JSON parse; delivered artifact sizes/SHA-256 match the manifest; the PowerShell runner parses with zero syntax errors. The repeatable script checked 147 text files from 153 non-ignored Git entries with zero limited-secret/link/whitespace findings. git diff --check passed, local tools/data/artifacts remain ignored, and no existing database migration changed. These are scoped checks, not comprehensive security certification.

Focused security review is AI-assisted, not an independent penetration test or formal proof. Single-host measurements do not establish large-chain/large-lifecycle/soak/WAN/TLS capacity. Global append locking, per-event lookups and buffered export are deliberate prototype costs. Operated external checkpoints, KMS/HSM, revocation/rotation, distributed quotas, tenant/legal-hold policies and legal compliance certification are absent. Backup/WAL/MVCC/in-flight/prior-copy limits constrain erasure; unprotected metadata remains sensitive. Signed sparse headers/status and producer-supplied actor/outcome do not prove complete selection, upstream truth or physical destruction.

External CI/Compose17.6, production database/JDK patch assessment, real key/backup operations, human acceptance and live defense, remote privacy/reviewer access and submission remain unverified. [Walkthrough](../walkthrough.md), [security review](../security-review.md), [summary](../final-engineering-summary.md), AI log and unsigned attestation state those boundaries. Actual prior M7 commit 085e332 observed; M8 uncommitted at delivery. No identity, signature, approval, commit, publication or submission was fabricated.
