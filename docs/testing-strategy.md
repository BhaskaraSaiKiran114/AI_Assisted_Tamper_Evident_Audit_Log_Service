# Testing and validation strategy

`./mvnw clean verify` (Windows `.\mvnw.cmd clean verify`) enforces Java 21, runs Checkstyle, compiles/tests and packages the Boot JAR. `FoundationIntegrationTest` starts H2/Flyway/Security and checks genesis initialization, minimal health and denied audit/docs access. It tests infrastructure, not future business behavior.

Surefire runs `*Test`; Failsafe is configured for future `*IT` tests. The H2 integration test intentionally runs in the mandatory Surefire gate without Docker. No scenario tests exist yet.

| Milestone | Checks to add |
| --- | --- |
| M2 | Canonical vectors, JSON/time/number rules, encryption representation, input bounds, links, parallel writes, rollback, no mutation APIs |
| M3 | Filters/combinations/time edges, stable pages, empty chain, changed content/columns/links, missing record/head mismatch, concurrent snapshot traversal |
| M4 | Missing/wrong/expired/not-yet-valid JWT, issuer/audience/algorithm, positive/negative permissions, safe errors, protected OpenAPI, restricted DB grants |
| M5 | Archive age/retries, intact archived chain, selective/repeated key erasure, denied operations, rollback and documented backup/export boundaries |
| M6 | Offline verifier, sparse proof, corrupted/reordered/missing content, wrong key/signature/version, archived/redacted export |
| M7 | Account-access vocabulary, permissions, account/actor/time/action/outcome filters, restricted fields and source completeness assumption |
| M8 | PostgreSQL A/B/C walkthrough, SQL tamper demonstration, fresh setup, security scan, measured performance and live-change rehearsal |

PostgreSQL establishes locking/isolation/types/privileges. H2 mode cannot establish those. Add actual reference-database automation in M2; do not silently skip it and claim success. If Docker is unavailable, use an explicitly configured disposable PostgreSQL instance or mark validation unexecuted.

Other gates:

- `./mvnw -Psecurity-audit verify` uses current external advisory data and fails on CVSS >=7. Configure `NVD_API_KEY` if available; never commit it. Normal verify is not a vulnerability scan. No unchecked suppressions.
- Review deliverables for credentials, sensitive fixtures and assignment copies; local tools/build/data/exports are ignored.
- Compiler/Checkstyle/whitespace checks are a small baseline, not full design/security proof.
- M8 performance exercise records hardware/JDK/DB/data volume/workload/concurrency, append latency percentiles/throughput, query timings and full-verification duration. Agree budgets with the engineer rather than inventing regulatory SLOs.

Evidence files record actual commands, environment, counts/results, failures/repairs and limits. A configured CI workflow is not an executed CI run. Keep raw build logs under ignored target/ and summarize without secrets.
