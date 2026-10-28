# Incremental implementation plan

Eight milestones, executed in dependency order. The user separately authorized M1-M8 on 2026-10-28; all engineering deliverables are now delivered. Authorization to implement is separate from human acceptance of the delivered output.

| Milestone | Dependencies | Intent and scope | Completion evidence | Status |
| --- | --- | --- | --- | --- |
| M1 Foundation | Architecture proposal and user instruction | Governance, requirements, ambiguity record, ADRs, traceability, Java/Maven bootstrap, database profiles, migration and quality gates | Java 21 build, migration/health/deny-all smoke tests, documentation and explicit limits | Complete, 2026-10-28; human acceptance pending |
| M2 Append-only ingestion | M1 | Exact canonicalization/golden vectors, protected payload representation, event schema, transaction lock, input validation | Required fields/bounds, stable hashes, genesis, rollback and concurrent append tests; no mutation API | Complete, 2026-10-28; 35 tests including 8 PostgreSQL tests; human acceptance pending |
| M3 Query and verification | M2 | Combined filters/cursors, snapshot verification, first inconsistency taxonomy | Direct datastore tampering detected; stable sparse pagination and unfiltered full traversal; archival behavior itself remains M5 | Complete, 2026-10-28; 71 tests including 24 PostgreSQL tests; human acceptance pending |
| M4 Security and contract | M2-M3 | JWT validation, permissions, safe errors, protected OpenAPI/Swagger, database privileges | Real signed-token/authority matrix, protected docs, restricted runtime pool and local setup examples | Complete, 2026-10-28; 89 tests including 26 PostgreSQL tests; human acceptance pending |
| M5 Retention and redaction | M2-M4 | Configurable archival, audited lifecycle actions and per-field key erasure | Eligibility, idempotency, intact chain, unreadable redacted values, permission/rollback tests | Complete, 2026-10-28; 126 tests including 41 PostgreSQL tests; human acceptance pending |
| M6 Verifiable export | M3-M5 | Filtered bundles, bridging headers, signed manifest and independent verifier | Verification without service; altered/missing/reordered content and wrong trust key rejected | Complete, 2026-10-28; 168 combined tests (57 PostgreSQL + 1 offline CLI); human acceptance pending |
| M7 Compliance reporting | M3-M6; C clarification in M1 | Bounded account-access report with explicit assumptions/scope | Authorized account/actor/time/action/outcome tests; no payload leakage | Complete, 2026-10-28; 207 tests (74 PostgreSQL + 1 offline CLI); human acceptance pending |
| M8 Final readiness | M1-M7 | PostgreSQL end-to-end, performance/security review, walkthrough and final summary | A/B/C evidence, all quality-gate results, authentic submission preparation; human acceptance remains separate | Engineering complete, 2026-10-28; 210 readiness checks and advisory gate passed; human acceptance/signature/submission pending |

## Every-milestone completion protocol

1. Compare output with assigned requirements/acceptance criteria and review the diff.
2. Run relevant checks; fix failures and record unresolved limitations accurately.
3. Update AI usage, unsigned attestation progress, traceability, validation ledger, milestone state and summary.
4. Notify the user of completion, checks, remaining limits and planning progress. Human acceptance remains separate.
5. Preserve authentic history when committing; do not fabricate commits or impersonate the candidate. External push, access grants and submission require authorization.

Security design starts in M1. M4 now enables implemented business routes only through validated JWTs and explicit permissions. M6 enables bounded protected export; M7 enables bounded metadata-only reporting through COMPLIANCE; unlisted routes remain denied. No public bypass or unauthenticated token-issuing endpoint is added.

## Timebox

The assessment describes a 2-3 day prototype. These are logical milestones, not promised durations. If the timebox cannot fit, propose a reasoned scope boundary for human approval rather than silently dropping requirements or claiming completion.
