# Security audit artifact diagnostics

2026-10-28. User reported that the security-audit push job failed with an upload error: the configured HTML report did not exist. The least-common-ancestor line is normal artifact-root information. No earlier Maven error was supplied, so the original scanner/build failure cannot be identified from the upload error or job name alone.

## Correction

- Current Maven HTML/JSON output defaults to target/security-audit, configured through audit.security.report-directory. Historical M8 reports under .tools/security-report retain their original meaning.
- The audit step creates the directory first and captures Maven stdout/stderr in maven.log with Bash pipefail. A failed Maven process remains a failed job; tee cannot hide it.
- An always-run diagnostic step records the audit outcome and nonempty-report presence in status.txt and the GitHub job summary. If the scanner never produced reports, this records that fact without fabricating a report.
- After a successful scan, both nonempty HTML and JSON reports are mandatory. Missing/empty output fails validation.
- The always-run artifact upload targets the non-hidden target/security-audit directory, retaining the log/status and any reports. CVSS >=7, failOnError=true and default feed updates remain enforced. No continue-on-error, ignored missing-artifact failure or TLS bypass was introduced.

## Actual validation

- Workflow YAML parsed with cached SnakeYAML. The actual run blocks were extracted and executed under Git Bash using synthetic Maven fixtures for six conditions: failed scan without reports, successful scan with both reports, missing JSON, empty HTML, nonzero vulnerability result with reports, and scan-not-run diagnostics. Failure status, logs, report checks and diagnostic artifacts behaved as intended. These are workflow simulations, not vulnerability scan results.
- Isolated Java 21/Maven clean verify passed at 23:33:58 +05:30 in 42.251 seconds: 151 Surefire plus one packaged-verifier check, zero failures/errors/skips, zero Checkstyle violations and all coverage gates passed. PostgreSQL was not rerun for this output-path/workflow change.
- Actual Dependency-Check report-generation smoke test passed at 23:34:50 +05:30 in 13.789 seconds, writing nonempty HTML/JSON to target/security-audit. The smoke test used the existing advisory database with autoUpdate=false locally; it does not establish current feed freshness or a completed GitHub run. CI keeps its default feed updates enabled.
- Tested pom.xml/workflow copies matched the current source hashes. Sanitized input/report digests and result summary: [security-audit-artifacts.json](security-audit-artifacts.json). Raw logs and reports remain ignored under .tools/security-artifact-work and .tools/security-artifact-*.log. The running application was not replaced.

## Diagnose the next remote run

Commit/push the reviewed fix and rerun Verify. In security-audit, open the Audit runtime dependencies step or download the security artifact and inspect maven.log. status.txt/job summary show whether report generation completed. If the scan fails, address its first Maven/scanner error; an absent report alone does not identify a network, credentials, feed, dependency or compilation problem. Do not mark the audit passed simply because diagnostics uploaded. NVD_API_KEY may be configured as a repository Actions secret where appropriate; never paste it into logs or repository files.

Sources: [Dependency-Check output/configuration contract](https://dependency-check.github.io/DependencyCheck/dependency-check-maven/check-mojo.html), [GitHub artifact missing-file behavior](https://github.com/actions/upload-artifact#customization-if-no-files-are-found).
