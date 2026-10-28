# Testing and validation strategy

`./mvnw clean verify` (Windows `.\mvnw.cmd clean verify`) enforces Java 21, runs Checkstyle, compiles/tests and packages the Boot JAR. `FoundationIntegrationTest` starts H2/Flyway/Security and checks genesis initialization, minimal health and denied audit/docs access. It tests infrastructure, not future business behavior.

Surefire runs *Test; normal verify now runs 138 unit/H2/security cases and one offline-verifier Failsafe case after packaging. Explicit postgres-it runs 75 Failsafe cases: the same offline test and 74 PostgreSQL cases (8 append, 16 read/verify, 13 lifecycle, 11 export, 12 report, 14 actual restricted-pool API/privilege cases), validated locally on PostgreSQL 16.3. Both disposable-database and cluster-role consent flags plus a fresh audit_m2_test database/cluster are mandatory. The Windows runner creates/stops its own cluster; CI's 17.6 service is configured, not externally executed. The M8 opt-in readiness gate passed 210 cases (the 207 regular cases plus three real HTTP/readiness exercises) with no failures/errors/skips.

| Milestone | Validation scope and progress |
| --- | --- |
| M2 | Implemented: canonical vectors, JSON/time/number rules, encryption, input bounds, links, parallel writes, rollback, no mutation APIs |
| M3 | Implemented: filters/time edges, sparse stable pages, cursor signing/binding, empty chain, tampering/links/gaps/head/schema checks, concurrent snapshot traversal, safe error contracts |
| M4 | Implemented: real signed tokens and required-claim/algorithm/time checks, seven-role permission matrix, safe auth errors, protected spec/UI assets, local key/token helper, verification capacity/release, real runtime-pool privilege and object-ownership proof |
| M5 | Implemented: archive age/retries, intact archived chain, selective/repeated key erasure, denied operations, rollback and documented backup/export boundaries |
| M6 | Implemented: offline verifier, sparse proof, corrupted/reordered/missing content, wrong key/signature/version, archived/redacted export |
| M7 | Implemented: account-access vocabulary, permissions, account/actor/time/action/outcome filters, restricted fields and source completeness assumption |
| M8 | Delivered: real HTTP PostgreSQL A/B/C, SQL tamper, separate packaged-H2 startup, advisory scan/remediation, measured baseline and written live-change rehearsal; actual human defense remains pending |

PostgreSQL establishes reference behavior; H2 alone cannot. M2 covers append transactions; M3 covers filtered reads, tampering and database-specific snapshots; M4 proves actual runtime credentials can append/read/verify while forbidden SQL fails 42501 and migration objects have the separate owner. Existing destructive contracts intentionally retain guarded administrator fixtures, never runtime production privileges. The profile fails rather than skips without both consent flags/configuration and refuses existing role names. Normal verify intentionally omits PostgreSQL. M5 adds both shared lifecycle contracts and actual restricted-pool archival/erasure tests, including denial of unaudited erasure and direct key deletion. M6 tests snapshot/export caps, source corruption, protected/redacted/archived exports, real EXPORT permissions/runtime pool and signed manifest/content/header tampering. The actual verifier JAR runs in a separate Java process with no dependencies, accepts a trusted key and rejects wrong-key/malformed bundles. M7 adds report contracts for boundaries/filters/outcomes, excluded/protected outcomes, metadata-only output/no key reads, archive opt-in, cursor domain/binding/sparse pages, source corruption, scan/concurrency limits and actual restricted-pool reporting. M8 adds three opt-in readiness exercises and final security/performance evidence. Tests/package remain distinct from external CI/Compose17.6 or production operations.

Other gates:

- `./mvnw -Psecurity-audit verify` uses current external advisory data and fails on CVSS >=7. Configure `NVD_API_KEY` if available; never commit it. Normal verify is not a vulnerability scan. No unchecked suppressions.
- Review deliverables for credentials, sensitive fixtures and assignment copies; local tools/build/data/exports are ignored.
- Compiler/Checkstyle/whitespace checks are a small baseline, not full design/security proof.
- M8 performance exercise recorded hardware/JDK/DB/data volume/workload/concurrency, append latency percentiles/throughput, query timings and full-verification duration. Agree budgets with the engineer rather than inventing regulatory SLOs.

Evidence files record actual commands, environment, counts/results, failures/repairs and limits. A configured CI workflow is not an executed CI run. Keep raw build logs under ignored target/ and summarize without secrets.

For the complete local readiness suite use scripts/verify-postgres.ps1 -Readiness or -Ppostgres-it,readiness with the same guarded disposable setup. scripts/check-deliverables.py provides optional Python 3 hygiene; it prints locations, never secret matches. Final metrics, advisory scope, failures/repairs, data freshness and artifact fingerprints are in the M8 ledger. A dependency audit is not a functional test, an incomplete import is not a clean scan, and repeated executions must not inflate unique test counts.

The local Swagger follow-up added four guard and two H2 HTTP cases. Its isolated normal clean verify passed 139; the earlier M8 210-case full readiness evidence is historical and was not re-executed for that follow-up. See validation/local-swagger.md.

## Resource authorization, measured coverage and CI evidence follow-up

The preceding milestone counts are historical. Current counts and executed results belong in [functional-gap-remediation](validation/functional-gap-remediation.md). Shared ResourceAuthorizationContract executes real signed JWT same-role/different-account attacks on H2 and PostgreSQL's restricted audit_runtime pool. It covers ingestion, guessed IDs, filter intersection, subject/assignment cursor replay, exports, report counts, targeted redaction/replay, missing/malformed assignments and archived access. ResourceScopeTest adds shape bounds, exact/wildcard semantics, parameter binding and canonical assignment ordering. Existing permission/tamper/transaction/independent-verifier suites remain.

JaCoCo instruments unit and integration JVMs into the same clean build, with XML/HTML reports and bundle line/branch/method gates 85%/75%/85%; security-package gates are 90%/80%/90%. No application classes/packages are excluded. These do not quantify endpoint completeness; use the risk mapping and actual negative tests. CLI subprocess bytecode is not agent-instrumented, although the artifact contract runs the real packaged CLI and ExportBundle unit tests instrument the same library code.

CI requires postgres-it plus readiness, and publishes source-input hashes, Git identity/run URL, raw test XML, coverage HTML/XML and readiness records under a SHA/run/attempt-specific artifact. Dependency audit runs in its own job and feed failures are failures. Test and coverage evidence completion rejects changed inputs, missing/empty required suites and any failure/error/skip. Run `python -m unittest discover -s scripts -p 'test_*.py'` for six evidence-gate regression tests (synthetic reports, not service-test counts).

For local evidence, run clean first, then `python scripts/build-evidence.py --begin`, then Maven verify, then `python scripts/build-evidence.py` (add `--reference` only after PostgreSQL plus readiness). The Windows reference runner supports `-EvidencePython <python-executable>` and optionally `-EvidenceSourceRoot <original-root>` for an isolated copy; input digests must match the actual build copy both before and after the run. Never create the begin manifest after running tests. Reports in target are ignored and must be retained with the submission; CI retention is 30 days.
