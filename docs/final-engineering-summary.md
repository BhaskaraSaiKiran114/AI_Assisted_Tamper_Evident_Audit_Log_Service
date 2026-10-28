# Engineering summary - living draft

Updated: 2026-10-28. Progress record, not a final readiness claim.

M1-M4 complete: engineering foundation, strict ingestion/canonical hashing, protected payloads, atomic append, filtered cursor queries, lookup/verification, real JWT permissions, protected OpenAPI/Swagger, safe auth errors, scan concurrency limits and separate PostgreSQL runtime/migration credentials. Clean verify passed 63 normal + 26 PostgreSQL 16.3 tests, no failures/errors/skips and zero Checkstyle violations. Executable JAR produced; isolated test cluster stopped. Evidence is in validation/milestone-1.md through milestone-4.md; encoding/read/security contracts describe exact behavior. Human acceptance is pending. Planning progress: 4/8 milestones (50%), not a functional accuracy score.

Rationale: one service/explicit JDBC keeps transaction reasoning visible; PostgreSQL is authoritative and H2 disposable. Archival preserves chain material. Encrypt-before-hash/key erasure offers bounded redaction. External signing trust is needed for export authenticity and stronger checkpoints.

M5-M8 remain unstarted: retention/redaction, verifiable export, compliance reporting and final readiness. Core business HTTP now requires validated JWTs and exact permissions; no event mutation route exists. Runtime database credentials cannot update/delete history or perform DDL, key deletion or role escalation; actual pool tests establish that boundary.

ReadSnapshot selects PostgreSQL REPEATABLE_READ / H2 SERIALIZABLE after a real H2 concurrency failure exposed phantom rows. Append isolation is unchanged; queries/verification start their own read-only snapshots. Cursors bind filters, size and upper head but do not freeze payload history across requests or prove filter completeness. Payload reads restore protected values; unavailable keys are errors until M5 defines audited redaction, while chain verification remains independent of keys.

M4 uses one pinned RS256 verification key, explicit permission strings and a separate DOCS authority. The local source launcher generates owner-restricted keys/five-minute developer tokens; it is not a deployed identity provider. Swagger HTML/assets require a header-capable browser/client. Full verification is bounded to two simultaneous scans per process by default; that is not distributed rate limiting or a measured SLO.

Risks: no production KMS, external checkpoint operation, regulator certification, upstream completeness or scale guarantee. Preserve the payload master key and use a distinct cursor key. Startup Flyway secrets remain in-process until operations separate migration jobs; existing volumes require a reviewed upgrade rather than silent takeover/reset. Global append locks, scans and per-event key lookups have costs. PostgreSQL 17.6/Compose/remote CI, advisory scan, load/performance, later scenarios, remote privacy/reviewer access, candidate signature and submission remain pending. Prior commits M1 522fac7, M2 45229d1, M3 a4a7780 observed; M4 is uncommitted at delivery.

For M8, fill actual artifacts/commit references, scenario demos, test/performance results, failures/repairs, accepted risks, confirmed assumptions, fresh-setup walkthrough, human acceptance and submission date. Preserve authentic history and AI decision linkage; do not convert the draft into a production certification.
