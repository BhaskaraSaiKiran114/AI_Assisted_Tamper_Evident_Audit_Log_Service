# Engineering governance

## Ownership and authority

The candidate owns correctness, execution, maintainability, authorship and readiness claims. AI assists bounded tasks and supplies reviewable outputs. M1, M2 and M3 were authorized separately; M4-M8 require further instruction. No agent has signed or submitted on the candidate's behalf.

The assignment is confidential context. Keep its source out of the repository and public services. Web checks use generic public technology queries. Use synthetic examples only; never submit credentials, tokens, private keys, client identifiers/payloads or unreviewed logs to AI.

## Approval boundaries

Routine edits and validation within an authorized milestone proceed without repeated approval. Human approval is required for architecture/trust-boundary changes, destructive actions against meaningful data, external publication/messages/access grants, production changes and final submission. Environment permission for a tool is not engineering acceptance.

The candidate confirms attestation identity and statement. Git config is not a signature. Do not invent sign-offs or retrospectively manufacture history.

## Task and AI review

Each milestone has intent, constraints, dependencies and acceptance criteria. AI entries record the human task, tools, generated/edited/rejected output and rationale, validation, risks and actual human review. Log material prompt refinements and discarded designs. A polished artifact alone is not correctness evidence.

## Quality gates

| Gate | Mechanism | Current evidence / remaining work |
| --- | --- | --- |
| Toolchain | Maven Enforcer: JDK 21; Maven >=3.9.9 and <4; compiler release 21 | Passed through M3; actual results in milestone ledgers |
| Static/style | Compiler diagnostics, Checkstyle at validate for main/test | Small baseline, not exhaustive static analysis |
| Tests/package | Surefire/Failsafe and executable Boot JAR via clean verify | M3: 47 unit/H2 + 24 PostgreSQL cases passed; later scenario suites follow implementation |
| Dependency security | Opt-in security-audit profile, CVSS >=7 failure | Requires current external data; no pass claimed until executed |
| Secrets | Ignore rules, diff review, targeted secret inspection | Local review through M3; dedicated automation by M8 |
| PostgreSQL | Real migrations, transaction/concurrency and tampering tests | M2/M3 passed on isolated PostgreSQL 16.3; deployment-role/17.6 checks remain later work |
| Performance | Recorded environment, workload, latency/throughput/verification time | M8; no invented production SLO |
| Human review | Requirement-linked artifact acceptance | Pending for delivered M1-M3 outputs |

CI runs on push/pull request when intentionally hosted. Adding a workflow is not executing it or authorization to publish. Dependency scanning may contact advisory services; never upload confidential source or secrets.

## Change and evidence rules

- Migrations are immutable after shared application; use new versions. Separate migration-owner/runtime credentials before deployment.
- Preserve cryptographic representations and golden vectors; use an ADR for encoding/trust changes.
- Record failures, repairs, reruns and actual results. Distinguish unavailable, failed and passed.
- Link requirements to artifacts/tests in traceability; keep limitations current in the engineering summary.
- Do not commit local tools/data/secrets/exports, build products or the confidential assignment. Do not add broad licensing claims over assessment material.
