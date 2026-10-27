# Incremental implementation plan

Eight milestones, executed in dependency order. The user authorized M1 on 2026-10-28 after the architecture proposal; M2-M8 are planned only. Authorization to build M1 is separate from human acceptance of its output.

| Milestone | Dependencies | Intent and scope | Completion evidence | Status |
| --- | --- | --- | --- | --- |
| M1 Foundation | Architecture proposal and user instruction | Governance, requirements, ambiguity record, ADRs, traceability, Java/Maven bootstrap, database profiles, migration and quality gates | Java 21 build, migration/health/deny-all smoke tests, documentation and explicit limits | Complete, 2026-10-28; human acceptance pending |
| M2 Append-only ingestion | M1 | Exact canonicalization/golden vectors, protected payload representation, event schema, transaction lock, input validation | Required fields/bounds, stable hashes, genesis, rollback and concurrent append tests; no mutation API | Not started |
| M3 Query and verification | M2 | Combined filters/cursors, snapshot verification, first inconsistency taxonomy | Direct datastore tampering detected; pagination and archived-ready full traversal | Not started |
| M4 Security and contract | M2-M3 | JWT validation, permissions, safe errors, protected OpenAPI/Swagger, database privileges | Token/authority matrix, valid role tests and contract examples | Not started |
| M5 Retention and redaction | M2-M4 | Configurable archival, audited lifecycle actions and per-field key erasure | Eligibility, idempotency, intact chain, unreadable redacted values, permission/rollback tests | Not started |
| M6 Verifiable export | M3-M5 | Filtered bundles, bridging headers, signed manifest and independent verifier | Verification without service; altered/missing/reordered content and wrong trust key rejected | Not started |
| M7 Compliance reporting | M3-M6; C clarification in M1 | Bounded account-access report with explicit assumptions/scope | Authorized account/actor/time/action/outcome tests; no payload leakage | Not started |
| M8 Final readiness | M1-M7 | PostgreSQL end-to-end, performance/security review, walkthrough and final summary | A/B/C evidence, all quality-gate results, human acceptance and authentic submission preparation | Not started |

## Every-milestone completion protocol

1. Compare output with assigned requirements/acceptance criteria and review the diff.
2. Run relevant checks; fix failures and record unresolved limitations accurately.
3. Update AI usage, unsigned attestation progress, traceability, validation ledger, milestone state and summary.
4. Notify the user of completion, checks, remaining limits and planning progress. Human acceptance remains separate.
5. Preserve authentic history when committing; do not fabricate commits or impersonate the candidate. External push, access grants and submission require authorization.

Security design starts in M1. Until M4, business access stays denied; M2/M3 can be tested below HTTP authorization. Do not add a temporary public bypass.

## Timebox

The assessment describes a 2-3 day prototype. These are logical milestones, not promised durations. If the timebox cannot fit, propose a reasoned scope boundary for human approval rather than silently dropping requirements or claiming completion.
