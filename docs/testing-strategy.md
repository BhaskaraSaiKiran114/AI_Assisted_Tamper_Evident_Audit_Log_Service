# Testing and validation strategy

`./mvnw clean verify` (Windows `.\mvnw.cmd clean verify`) enforces Java 21, runs Checkstyle, compiles/tests and packages the Boot JAR. `FoundationIntegrationTest` starts H2/Flyway/Security and checks genesis initialization, minimal health and denied audit/docs access. It tests infrastructure, not future business behavior.

Surefire runs *Test; normal verify runs 132 unit/H2/security cases and one offline-verifier Failsafe case after packaging. Explicit postgres-it runs 75 Failsafe cases: the same offline test and 74 PostgreSQL cases (8 append, 16 read/verify, 13 lifecycle, 11 export, 12 report, 14 actual restricted-pool API/privilege cases), validated locally on PostgreSQL 16.3. Both disposable-database and cluster-role consent flags plus a fresh audit_m2_test database/cluster are mandatory. The Windows runner creates/stops its own cluster; CI's 17.6 service is configured, not externally executed. The M8 opt-in readiness gate passed 210 cases (the 207 regular cases plus three real HTTP/readiness exercises) with no failures/errors/skips.

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
