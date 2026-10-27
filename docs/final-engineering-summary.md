# Engineering summary - living draft

Updated: 2026-10-28. Progress record, not a final readiness claim.

M1 and M2 are complete: governance/design foundation plus strict ingestion, deterministic canonical hashing, encrypted field representation, event/key schema, row-locked atomic append and tests. Java 21 clean verify passed with 27 unit/H2 tests and 8 PostgreSQL 16.3 tests, no failures/errors/skips and zero Checkstyle violations. An executable JAR was produced and the isolated test cluster stopped. Evidence is in `validation/milestone-1.md` and `validation/milestone-2.md`; exact encoding is in `encoding-v1.md`. Human acceptance remains pending. Planning progress: 2/8 milestones (25%), not a functional accuracy score.

Rationale: one service/explicit JDBC keeps transaction reasoning visible; PostgreSQL is authoritative and H2 disposable. Archival preserves chain material. Encrypt-before-hash/key erasure offers bounded redaction. External signing trust is needed for export authenticity and stronger checkpoints.

M3-M8 remain unstarted: query/full verification, JWT/permissions/OpenAPI, lifecycle, exports, compliance and final readiness. Business HTTP remains denied pending M4 despite the implemented ingestion adapter. No event update/delete route exists; restricted runtime DB privileges remain M4. Dependency presence is not feature completion.

Risks: no production KMS, external checkpoint operation, regulator certification, upstream completeness or scale guarantee. Master key must be secured outside PostgreSQL and preserved across restarts. Global append locking and full scans have costs. PostgreSQL 17.6/scenario/privilege checks, advisory scan, performance evidence, external CI execution, remote privacy/reviewer access, candidate identity/signature and submission remain pending. M1 commit 522fac7 exists; M2 changes are uncommitted at delivery.

For M8, fill actual artifacts/commit references, scenario demos, test/performance results, failures/repairs, accepted risks, confirmed assumptions, fresh-setup walkthrough, human acceptance and submission date. Preserve authentic history and AI decision linkage; do not convert the draft into a production certification.
