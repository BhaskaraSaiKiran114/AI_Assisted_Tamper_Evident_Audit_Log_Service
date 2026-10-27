# Engineering summary - living draft

Updated: 2026-10-28. Progress record, not a final readiness claim.

M1 is complete: requirements for A/B/C, an eight-milestone plan, architecture, ADRs, threat model, governance/testing rules, AI traceability and unsigned attestation are delivered. Foundation code contains bootstrap, profiles, genesis migration, deny-all security and smoke tests. Java 21 clean verify passed with 3 tests, zero failures/errors/skips and zero Checkstyle violations; an executable Boot JAR was produced. Evidence and boundaries are in `validation/milestone-1.md`. Human acceptance is pending. Planning progress: 1/8 milestones (12.5%).

Rationale: one service/explicit JDBC keeps transaction reasoning visible; PostgreSQL is authoritative and H2 disposable. Archival preserves chain material. Encrypt-before-hash/key erasure offers bounded redaction. External signing trust is needed for export authenticity and stronger checkpoints.

M2-M8 remain unstarted: business ingestion/chain, query/verify, JWT/permissions/OpenAPI, lifecycle, exports, compliance and final reference validation. Dependency presence is not feature completion.

Risks: no production KMS, external checkpoint operation, regulator certification, upstream completeness or scale guarantee. Global append locking and full scans have costs. PostgreSQL tests, advisory scan, performance evidence, external CI execution, private remote/reviewer access, candidate identity/signature and submission remain pending unless later evidence records otherwise.

For M8, fill actual artifacts/commit references, scenario demos, test/performance results, failures/repairs, accepted risks, confirmed assumptions, fresh-setup walkthrough, human acceptance and submission date. Preserve authentic history and AI decision linkage; do not convert the draft into a production certification.
