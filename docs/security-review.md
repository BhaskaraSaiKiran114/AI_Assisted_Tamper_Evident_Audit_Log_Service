# M8 focused security review

Scope: AI-assisted local source/configuration review and adversarial tests, not an independent penetration test, formal cryptographic proof or production certification. Human acceptance remains pending. Review covers JWT policy, safe errors, strict input bounds, JDBC query construction, transaction/evidence boundaries, role grants, key disclosure, exported input parsing and artifact/secret hygiene.

| Boundary | Reviewed implementation/evidence | Conclusion / remaining work |
| --- | --- | --- |
| Authentication and permissions | Pinned RS256 RSA key, issuer/audience/time/subject checks, explicit independent permissions, header-only/stateless policy, signed-token suites | No new bypass found in this focused review; issuer/token lifecycle and tenant/reviewer scope remain operational decisions |
| Input/query/error handling | Bounded strict JSON, recognized query parameters and parameterized dynamic predicates; safe ProblemDetail and no request/token logging added | Adversarial parser/SQL-metacharacter/permission tests pass; this is not exhaustive fuzzing |
| Immutable chain and transaction | Frozen byte/hash format, locked atomic append, snapshot verification, rollback/concurrency/direct SQL tamper suites | Runtime history mutation denied; a coherent administrator rewrite still requires external evidence to detect |
| Lifecycle and key privacy | Same-transaction administrative proof/markers/erasure; PostgreSQL scoped SECURITY DEFINER with qualified relations/fixed search_path/PUBLIC execute revoked | No direct runtime key DELETE; process/credential compromise can manufacture future allowed inserts/evidence; backups/WAL/MVCC/caches/prior copies limit erasure |
| Query/report/export disclosure | Separate READ/COMPLIANCE/EXPORT, extra LIFECYCLE for archives, metadata-only report and protected-byte export | No field keys/decrypted protected values in report/export; unprotected selected data and account/actor metadata remain sensitive |
| Export recipient trust and parsing | Pinned external public key, exact signed manifest/body, bounded ZIP entries/framing and standalone verifier tests | Hidden-content links/status rely on signer; no selection/upstream completeness proof; verifier software/trust-key distribution must be authenticated |
| Offline file read bounds | Size check previously preceded unbounded Files.readAllBytes/readString in CLI | Hardened to bounded reads plus a second length check, including input growth after size inspection; actual packaged CLI rejects oversized key/bundle inputs |
| Keys and deployment secrets | External persistent master key, separate signing/verification/cursor purposes, ignored owner-restricted developer private files, separate migration/runtime logins | No KMS/HSM, revocation/rotation operation or complete memory-zeroization guarantee; startup Flyway credentials remain in-process until production separates the migration job |
| Resource amplification | Request/page/candidate/proof/byte/time/concurrency bounds and failure-release tests | Bounds are per process and database timeouts, not distributed quotas or CPU/wall-clock SLOs; local measurements do not prove large-scale readiness |

## Advisory audit method and status

The security-audit profile keeps failBuildOnCVSS=7 and explicitly failOnError=true; no blanket suppression or missing-data bypass is added. Cache lives under ignored .tools; current reports default to target/security-audit (audit.security.report-directory), and the data-cache path is separately overridable to isolate retries. NVD_API_KEY is read from environment, not passed as a secret command argument or debug log.

The anonymous NVD API attempt encountered repeated request failures and an incomplete bulk import. It was stopped and is not a pass. An initial feed retry waited on the prior cache lock; only this task's matching Dependency-Check process was stopped, and a separately configured cache was used. The next attempt uses Dependency-Check's own maintained NVD mirror over HTTPS. The [official mirror guidance](https://dependency-check.github.io/DependencyCheck/data/mirrornvd.html) documents feed alternatives and their freshness/availability limits; a mirror is not proof of current complete coverage. The [Maven goal documentation](https://dependency-check.github.io/DependencyCheck/dependency-check-maven/check-mojo.html) documents the environment-key option and failure-related controls. No TLS verification is disabled.

The completed initial scan failed the CVSS >=7 gate. Component-range flags were reviewed against upstream advisories and patched without asserting that every affected feature was reachable in this application. No suppression file was added. The requested Boot 4.1.0 baseline is retained; compatible dependency versions are explicit in pom.xml:

| Component | Original | Patched | Evidence / reason |
| --- | --- | --- | --- |
| Spring Framework | 7.0.8 | 7.0.10 | Published patch metadata and [upstream affected/fixed ranges](https://spring.io/security/cve-2026-59313/) |
| Spring Security | 7.1.0 | 7.1.2 | Published patch metadata and [upstream affected/fixed ranges](https://spring.io/security/cve-2026-59270/) |
| Tomcat embedded modules | 11.0.22 | 11.0.27 | [Apache's released fixes](https://tomcat.apache.org/security-11.html) |
| PostgreSQL JDBC | 42.7.11 | 42.7.14 | [Driver channel-binding advisory](https://github.com/pgjdbc/pgjdbc/security/advisories/GHSA-j92g-9f8w-j867) and published patch metadata |
| Legacy Jackson 2 BOM | 2.21.4 | 2.21.8 | Advisory scan flagged CVE-2026-54515; compatible patch line published in Maven Central |
| Log4j2 | 2.25.4 | 2.25.5 | Advisory scan flagged CVE-2026-49844; compatible patch line published in Maven Central |
| Swagger UI WebJar | 5.32.11 | 5.34.0 | RetireJS flagged bundled DOMPurify; patched WebJar removes detected findings |

Springdoc's runtime asset version is bound to the Maven Swagger property through filtered application.yml. The first patched regression caught an authorized UI 404 caused by the older default asset path; configuration was aligned and the original HTML/CSS authorization assertions passed unchanged. Springdoc remains 3.1.0 and the application's own frozen canonical/hash formats are unchanged. Patch overrides require regression review when the Boot baseline is eventually upgraded; do not retain obsolete overrides blindly.

The interim 12.1.8 patched rescan passed its threshold, but review of its import log found two dropped CVE records caused by URL columns limited to 1000 characters. That incomplete import is not the final evidence. The audit tool was upgraded to the published [13.0.0 release](https://github.com/dependency-check/DependencyCheck/releases/tag/v13.0.0), with a fresh cache and full feed import. It **passed at 15:06:14 +05:30**, 59 dependency records, zero reported vulnerable dependency records and no CVE import errors logged. No project suppression file or error bypass was added.

Final NVD mirror data was last modified 2026-10-27T16:17:33Z and checked 2026-10-28T09:33:21Z. NVD/CPE and RetireJS analysis ran; **OSS Index automatically disabled itself because no credentials were available**. The compile/runtime default scan does not establish coverage of test-only dependencies, JDK/server binaries, all configuration-dependent exploitability or future advisories. Sanitized metadata: validation/milestone-8-security.json; raw HTML/JSON are ignored under .tools/security-report. No TLS validation bypass or fabricated analyzer credentials was used.

To reproduce with a new cache using the supported mirror (Maven online mode is required by this goal):

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Psecurity-audit org.owasp:dependency-check-maven:13.0.0:check '-DnvdDatafeedUrl=https://dependency-check.github.io/DependencyCheck_Builder/nvd_cache/nvdcve-{0}.json.gz'
```

Default cache is .tools/dependency-check-v13; override audit.security.data-directory to isolate another cache. Database updates remain enabled by default; autoUpdate=false was used only for the documented intermediate comparison, never to pretend the initial incomplete API import was current. New data/credentials may change findings. Dependency scanning is independent of functional-test success.

## Release ownership

Operational risks above are disclosed, not accepted on the candidate's behalf. Production release requires review of advisory findings/data coverage, intended PostgreSQL patch/image, runtime/migration separation, key/backup/privacy operations, externally retained evidence, tenant/reviewer scope and representative load. Human attestation/acceptance, remote repository privacy/reviewer access and submission remain unsigned/unverified until the owner acts. The prototype's local demonstration does not certify these conditions.

## Approved resource authorization and CI follow-up

ADR-010 supersedes the historical service-wide authorization policy. Exact issuer resource assignments now constrain reads, exports, reports, ingestion and targeted erasure; global verification/retention require explicit global assignment as well as the independent action permission. Same-role/different-resource contracts execute on H2 and the restricted PostgreSQL runtime pool, including cursor/context and pre-replay authorization attacks. The shared-chain head/hash metadata disclosure is documented rather than represented as tenant-private proof.

Measured JaCoCo gates and revision/input-bound artifacts supplement the earlier test ledger. A separate CI dependency-audit job now fails on feed errors and publishes its actual report. Configuring that job is not evidence of a completed remote or fresh local vulnerability scan. Runtime dependencies did not change in this follow-up; JaCoCo is a build plugin. Existing M8 advisory findings/results retain their original timestamps and coverage limits. Candidate acceptance, issuer assignment administration, real key lifecycle and backup operations remain outstanding production/human responsibilities.

## Security audit artifact failure follow-up

Current CI retains Maven logs and report-presence diagnostics under target/security-audit even when scanning fails. Successful scans must produce both nonempty report formats; underlying Maven/CVSS/feed failures remain failures. See [artifact validation](validation/security-audit-artifacts.md). The old .tools/security-report paths above describe the historical M8 scan. The new local output-path smoke test used the existing cache and is not fresh advisory evidence or remote CI success.
