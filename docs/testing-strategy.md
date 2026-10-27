# Testing and validation strategy

`./mvnw clean verify` (Windows `.\mvnw.cmd clean verify`) enforces Java 21, runs Checkstyle, compiles/tests and packages the Boot JAR. `FoundationIntegrationTest` starts H2/Flyway/Security and checks genesis initialization, minimal health and denied audit/docs access. It tests infrastructure, not future business behavior.

Surefire runs `*Test`; normal verify runs 27 unit/H2 cases without Docker. The explicit postgres-it profile enables Failsafe `*IT` tests: 8 shared append-contract tests validated locally on PostgreSQL 16.3. Consent and an isolated audit_m2_test database are required. The Windows runner creates/stops a new cluster; CI is configured for a disposable PostgreSQL 17.6 service, whose external execution is not yet claimed. See M2 validation and README for commands.

| Milestone | Checks to add |
| --- | --- |
| M2 | Canonical vectors, JSON/time/number rules, encryption representation, input bounds, links, parallel writes, rollback, no mutation APIs |
| M3 | Filters/combinations/time edges, stable pages, empty chain, changed content/columns/links, missing record/head mismatch, concurrent snapshot traversal |
| M4 | Missing/wrong/expired/not-yet-valid JWT, issuer/audience/algorithm, positive/negative permissions, safe errors, protected OpenAPI, restricted DB grants |
| M5 | Archive age/retries, intact archived chain, selective/repeated key erasure, denied operations, rollback and documented backup/export boundaries |
| M6 | Offline verifier, sparse proof, corrupted/reordered/missing content, wrong key/signature/version, archived/redacted export |
| M7 | Account-access vocabulary, permissions, account/actor/time/action/outcome filters, restricted fields and source completeness assumption |
| M8 | PostgreSQL A/B/C walkthrough, SQL tamper demonstration, fresh setup, security scan, measured performance and live-change rehearsal |

PostgreSQL establishes reference locking/transaction/type behavior; H2 cannot establish those. M2 checks append locking, atomic rollback and column types on a real isolated instance. Restricted runtime grants and later scenario behaviors remain untested. The postgres-it profile fails rather than skips when disposable-database consent/configuration is absent; normal verify intentionally omits reference tests and must be described as unit/H2-only.

Other gates:

- `./mvnw -Psecurity-audit verify` uses current external advisory data and fails on CVSS >=7. Configure `NVD_API_KEY` if available; never commit it. Normal verify is not a vulnerability scan. No unchecked suppressions.
- Review deliverables for credentials, sensitive fixtures and assignment copies; local tools/build/data/exports are ignored.
- Compiler/Checkstyle/whitespace checks are a small baseline, not full design/security proof.
- M8 performance exercise records hardware/JDK/DB/data volume/workload/concurrency, append latency percentiles/throughput, query timings and full-verification duration. Agree budgets with the engineer rather than inventing regulatory SLOs.

Evidence files record actual commands, environment, counts/results, failures/repairs and limits. A configured CI workflow is not an executed CI run. Keep raw build logs under ignored target/ and summarize without secrets.
