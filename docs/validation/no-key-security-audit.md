# Security audit without a candidate NVD API key

2026-10-28, Asia/Calcutta. User requested solving the CI failure without obtaining an NVD API key. The supplied trace showed `Invalid API Key, length of 0` and `NoDataException: No documents exist`. Previous cached local report-path checks did not establish cold-runner API access.

## Implemented change

The CI audit command now passes `-DnvdDatafeedUrl=https://dependency-check.github.io/DependencyCheck_Builder/nvd_cache/nvdcve-{0}.json.gz` and no longer binds an NVD_API_KEY repository secret. Explicit quoting retains the literal feed-template placeholder. The scanner uses the maintained public NVD JSON 2.0 feed instead of the REST API. README includes the same local command. Maven's existing optional API environment setting remains available for direct-API local use; CI chooses feed mode explicitly.

No application/schema/hash/permission/dependency-version change. Feed updates stay enabled; no autoUpdate=false, failOnError=false, continue-on-error, TLS bypass or suppression was introduced. Vulnerability findings and feed failures still fail the job. Logs/status/mandatory HTML+JSON reports and artifact preservation remain as implemented in the artifact-diagnostics follow-up.

## Actual validation

Isolated Java 21/Maven clean verify passed at 23:54:10 +05:30: 151 Surefire plus one packaged verifier check, zero failures/errors/skips/style violations, all coverage gates passed. YAML parsed and the actual run blocks passed six synthetic shell cases without an API-key environment variable, validating the exact mirror argument plus success/failure/report behavior. These fixtures do not claim a real vulnerability scan.

The real cold-cache scan **passed at 23:57:22 +05:30 in 02:51 min**, with NVD_API_KEY absent, feed updates enabled and a newly populated isolated vulnerability cache (.tools/nvd-no-key-fresh). Both nonempty HTML and JSON reports were generated; 59 dependency records, zero reported vulnerable dependency records and no ERROR lines in the scanner log. Reported data-source last modified was 2026-10-28T16:57:05Z and last checked 2026-10-28T18:24:37Z. The report retains Dependency-Check's NVD API/cache metadata labels even in data-feed mode; the actual command/download logs establish mirror retrieval. NVD/CPE, RetireJS and CISA analysis ran. OSS Index disabled itself for lack of its separate credentials; no credentials were invented and this limitation is retained. These results do not certify all software safe. Sanitized results, source timestamp and report/workflow digests are in [no-key-security-audit.json](no-key-security-audit.json). Raw ignored logs: .tools/no-key-audit-verify.log and .tools/no-key-audit-scan.log; isolated application/reports: .tools/no-key-audit-work. No remote GitHub rerun or PostgreSQL rerun is claimed.

## Trust and freshness limits

[Dependency-Check documentation](https://dependency-check.github.io/DependencyCheck/data/mirrornvd.html) describes its own public mirror as updated every 24 hours on a best-effort basis. It can retain stale data during upstream outages and has no guaranteed freshness SLA. The change removes the personal key requirement, while retaining a network/data-source dependency. Report data-source timestamps constrain security evidence; it does not establish all dependencies safe or replace production patch management.

Base revision for this follow-up: 03bff5c (observed committed report-diagnostics change). This workflow change is uncommitted at delivery unless the human commits it. No publication, secret creation, personal attestation or final acceptance was performed on the user's behalf.
