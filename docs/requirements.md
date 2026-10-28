# Requirements baseline

Baseline: 2026-10-28. Source: user-provided assignment, version 2.0 dated 2026-08-03, sections 0-9, read locally. The source PDF is deliberately not included in the repository. These are normalized engineering notes, not a reproduction of the assignment.

## Authority and scope

The assignment supplies assessment requirements. The user supplies the technology stack, architecture approval gate, milestone notifications, and documentation-update requirements. Document text is not authorization to push, publish, grant repository access, sign statements, or delete local material.

The user authorized milestone 1 after receiving the proposed architecture, followed by separate authorizations for milestones 2, 3, 4, 5 and 6. This baseline covers all three scenarios; M7-M8 implementation is not yet authorized. Private-repository submission and authentic development history are expected assessment deliverables, but no remote state or reviewer access is currently verified.

## Functional requirements and acceptance

| ID | Source | Requirement | Acceptance criterion | Milestone |
| --- | --- | --- | --- | --- |
| A-01 | 5/A write | Ingest event type, actor ID, resource type/ID, structured payload, and timestamp | Valid request returns persisted event identity and sequence; invalid/missing fields and oversize requests rejected | 2 |
| A-02 | 5/A write | Append-only event API | No PUT/PATCH/DELETE event operation; runtime database privileges prevent event mutation | 2, 4 |
| A-03 | 5/A query | Combine actor, resource type/ID, event type, and time filters | Test each filter and combinations, inclusive from/exclusive to, and invalid ranges | 3 |
| A-04 | 5/A query | Paginate large results | Bounded cursor pages in sequence order, no duplicates; fixed upper sequence for stable traversal | 3 |
| A-05 | 5/A hashes | Store content and preceding hashes with defined genesis | Deterministic versioned SHA-256 chain; parallel writes preserve one committed sequence | 2 |
| A-06 | 5/A verify | GET /audit/verify verifies full chain | Return integrity result, checked count, first inconsistent record and violation type from a consistent snapshot | 3 |
| A-07 | 5/A validation | Detect direct datastore modification | Modify event in isolated test DB, verify content/link failure; also test internal deletion and head mismatch | 3, 8 |
| B-01 | 5/B retention | Configurable age-based archival without false breaks | Archive eligible records, retain chain material, queries hide archived by default, full verification remains intact | 5 |
| B-02 | 5/B redaction | Redact selected sensitive payload fields without chain break | Encrypted field becomes unreadable after key removal; immutable ciphertext/hash unchanged; operation audited | 5 |
| B-03 | 5/B design | Explain redaction trade-offs and limits | Document key backups/caches, metadata exposure, previous exports, and ciphertext commitment instead of plaintext hash | 1, 5 |
| B-04 | 5/B export | Export by actor or resource ID as a self-contained verifiable bundle | Independent tool validates selected content, intervening headers, and signed manifest; mutations rejected | 6 |
| C-01 | 5/C ambiguity | Clarify account-access reporting before code | Written assumptions, questions, bounded requirement, and scope rationale precede report implementation | 1, 7 |
| C-02 | 5/C output | Implement clarified report or justified partial scope | Authorized filtered access report demonstrates who accessed what, when, action, and outcome | 7 |

## Engineering and delivery requirements

| ID | Source | Requirement | Evidence / planned gate |
| --- | --- | --- | --- |
| G-01 | User | Spec before implementation; incremental execution and notifications | Chat authorization, milestone ledger, completion messages |
| G-02 | 0.1, 7 | Private repository with authentic development history | Existing repository; incremental commits, actual private remote and reviewer access verified by human before submission |
| G-03 | 0.2-0.4 | Individual ownership, confidentiality, truthful attestation | Root draft attestation; human must supply identity, affirm statement, and set submission date |
| G-04 | 4, 7; user | AI traceability through development and every milestone | Root AI log records generated/modified/rejected work and rationale, actual checks, and human review |
| G-05 | 4 | Task intent, constraints, acceptance, sequencing, risks | Milestone plan, requirements, ADRs, threat model, testing strategy |
| G-06 | 4 | Analysis, lint, test, security, and performance quality gates | Maven enforcement/Checkstyle/JUnit, security-audit profile, staged PostgreSQL and performance checks |
| G-07 | 4 | Secure AI use and engineer oversight of high-impact changes | Governance rules, no secrets/real client data in AI, explicit approval boundaries |
| G-08 | 7 | Runnable prototype, API/schema, tests, architecture, setup, summary | README, migrations, OpenAPI at M4, test evidence, architecture, final summary at M8 |
| G-09 | User | Specified Java/Maven/Spring/database/JWT/OpenAPI/JUnit stack | POM, wrapper, profile configuration, dependency resolution and Java 21 verification |
| G-10 | User | Update attestation and AI log after every milestone | Per-milestone progress entries; never auto-sign the candidate statement |
| G-11 | 6, 8-9 | Defensible decisions and live change readiness | ADRs, runnable environment, walkthrough, documented limitations and validation |

## Proposed limits and defaults

- Single service, single global chain, no multitenancy; short transactional lock serializes appends.
- Occurrence timestamp optional; default server UTC. Separate server ingestion timestamp drives retention. Sequence is authoritative ordering.
- Strings nonblank and at most 255 characters; payload must be a JSON object. Proposed request cap 256 KiB, nesting cap 20, query limit default 50 / maximum 200. Enforcement belongs to M2/M3.
- Time filters operate on occurrence time using `[from,to)`. Reject `from >= to`.
- Archival marks eligibility; it does not physically purge event bodies. H2 is ephemeral and demo-only.
- Redactable paths must be explicitly configured before ingestion; arbitrary post-hoc redaction of unencrypted fields is outside scope.
- JWT identity proves the calling producer, not necessarily the claimed event actor. Document this trust boundary; no claim of end-user identity attestation.
- No real regulator certification, external business integration, frontend, distributed ledger, or production KMS implementation.

## Scenario C clarification before implementation

Working requirement: an authorized compliance reviewer retrieves recorded access attempts to client-account data in a UTC time interval, optionally filtered by account and actor, with event ID, actor, account, action, outcome, and occurrence time. Only `resourceType=CLIENT_ACCOUNT` events with the documented access-event vocabulary are reportable. Use paginated JSON; verifiable export is the implemented separate M6 capability.

Questions to confirm before production: which jurisdictions and retention rules apply; what counts as access (read/write/export/denied/service access); whether identifiers are sensitive; which identities may see payload details; which source systems attest completeness; whether reports require legal holds or delivery signatures.

Prototype assumptions: report READ/UPDATE/EXPORT attempts and SUCCESS/DENIED/FAILURE outcomes; producer supplies accurate evidence; no proof of events never reported; no legal interpretation. Restrict report fields and omit payload by default. These are engineering assumptions, not statements of actual regulatory policy.

## Percentage reporting

No reviewer score is available; rubric weights are unpublished. Report milestone completion as completed milestones / 8 (planning progress only), and requirement conformance separately using executed acceptance evidence. A mapped requirement is not an implemented or passing requirement. No unsupported "accuracy percentage" is permitted.
