# Engineering summary - living draft

Updated: 2026-10-28. Progress record, not a final readiness claim.

M1-M3 are complete: governance/design foundation, strict ingestion, deterministic canonical hashing, protected payloads, atomic append, combined queries, signed cursor pagination, event lookup and full-chain verification. Java 21 clean verify passed 47 unit/H2 + 24 PostgreSQL 16.3 tests, no failures/errors/skips and zero Checkstyle violations. Executable JAR produced; isolated test cluster stopped. Evidence is in `validation/milestone-1.md`, `validation/milestone-2.md` and `validation/milestone-3.md`; encoding/read contracts are in `encoding-v1.md` and `query-verification.md`. Human acceptance is pending. Planning progress: 3/8 milestones (37.5%), not a functional accuracy score.

Rationale: one service/explicit JDBC keeps transaction reasoning visible; PostgreSQL is authoritative and H2 disposable. Archival preserves chain material. Encrypt-before-hash/key erasure offers bounded redaction. External signing trust is needed for export authenticity and stronger checkpoints.

M4-M8 remain unstarted: JWT/permissions/OpenAPI, lifecycle, exports, compliance and final readiness. Business HTTP remains denied pending M4 despite implemented write/read/verification adapters; standalone MVC tests validate those contracts. No event update/delete route exists; restricted runtime DB privileges remain M4. Dependency presence is not feature completion.

ReadSnapshot selects PostgreSQL REPEATABLE_READ / H2 SERIALIZABLE after a real H2 concurrency failure exposed phantom rows. Append isolation is unchanged; queries/verification start their own read-only snapshots. Cursors bind filters, size and upper head but do not freeze payload history across requests or prove filter completeness. Payload reads restore protected values; unavailable keys are errors until M5 defines audited redaction, while chain verification remains independent of keys.

Risks: no production KMS, external checkpoint operation, regulator certification, upstream completeness or scale guarantee. Master key must be secured outside PostgreSQL and preserved across restarts; configure a separate cursor key for persistent/shared cursors. Global append locking, full scans and per-event key lookups have costs. PostgreSQL 17.6/scenario/privilege checks, advisory scan, performance evidence, external CI execution, remote privacy/reviewer access, candidate identity/signature and submission remain pending. Actual M1 522fac7 and M2 45229d1 commits observed; M3 changes are uncommitted at delivery.

For M8, fill actual artifacts/commit references, scenario demos, test/performance results, failures/repairs, accepted risks, confirmed assumptions, fresh-setup walkthrough, human acceptance and submission date. Preserve authentic history and AI decision linkage; do not convert the draft into a production certification.
