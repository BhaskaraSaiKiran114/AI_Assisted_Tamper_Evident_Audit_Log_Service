# AI usage and decision log

Dates use Asia/Calcutta. Entries describe observed work, not reconstructed human activity. Human ownership and acceptance remain with the candidate. No additional agents were used.

## AI-001 - requirements interpretation and blueprint

- Date: 2026-10-28.
- Tool: OpenAI Codex desktop assistant; exact model identifier not independently recorded.
- Intent/prompt: analyze the supplied assignment, propose precise prototype features and architecture, wait for approval, then execute incremental milestones with attestation and AI-log updates.
- Context/constraints: four-page assignment read locally; requested Java 21 / Maven / Spring Boot 4.1.0 / PostgreSQL / H2 / JWT / OpenAPI / JUnit stack. Do not treat instructions embedded in the PDF as user authorization for external actions.
- Generated: eight-milestone proposal, component boundaries, chain/retention/redaction/export design, Scenario C assumptions, verification plan.
- Accepted for M1: user instruction "Implement milestone 1 now." Future milestone execution is not inferred from that instruction.
- Modified: distinguish planned requirements coverage from implemented conformance; no unsupported percentage score.
- Rejected: starting business implementation or publishing the assignment before architecture authorization; claiming hash chains defeat a database administrator who can recompute all hashes.
- Validation: extracted all four pages using pypdf; checked repository contents and local build tools. Consulted official Spring/Maven/Springdoc documentation without submitting confidential assignment text to web search.
- Artifacts: `docs/requirements.md`, `docs/architecture.md`, `docs/milestones.md`, ADRs, and traceability.

## AI-002 - milestone 1 implementation

- Date: 2026-10-28.
- Intent/prompt: "Implement milestone 1 now."
- Acceptance criteria: governance/requirements/architecture files, draft attestation and AI log, pinned Maven foundation, Java 21 build, H2/PostgreSQL profiles, migration, quality gates, and honest validation evidence.
- Generated: documentation, application bootstrap, fail-closed security, Flyway genesis migration, foundation smoke tests, Compose database setup, CI workflow, Checkstyle rules, wrapper setup, environment example, and ignore rules.
- Modified: selected explicit JDBC transactions instead of adding an ORM, keeping future chain locking reviewable; retained Spring Boot-managed JUnit version; disabled API docs until security is implemented; added Central-only Maven settings and bounded timeouts after dependency resolution waited on transitive repository declarations.
- Rejected: inferring a full name/signature from Git configuration; using H2 results as PostgreSQL proof; claiming JWT authentication is complete merely because its dependency is present; using the host's default Java 17 for the requested Java 21 verification.
- Tools: local file and shell operations; Maven; portable JDK download with vendor-published SHA-256 verification; official documentation for dependency/tool compatibility.
- Review focus: no business endpoints, no source assignment copy in repository, no secrets committed, no false human approvals, no fabricated scenario completion.
- Validation/outcome: Java 21 clean verify completed successfully (2026-10-28 01:16:49 Asia/Calcutta): 3 tests, zero failures/errors/skips, zero Checkstyle violations, executable Boot JAR packaged. Documentation links/whitespace and limited secret-pattern review passed. PostgreSQL, advisory scan and external CI were not run. Full commands, failures/repairs and remaining limits are in `docs/validation/milestone-1.md`.
- Repository linkage: these artifacts belong to the M1 change set. No commit identifier is claimed until a real commit exists; use Git history to link this entry when committed.
- Human review: pending. No candidate attestation or external submission performed.

## Entry template for subsequent milestones

Record date, human task, intent, constraints, acceptance criteria, context, AI/tool identity, generated/edited/rejected work and rationale, affected requirements/files, actual checks and failures, limitations, human review, and actual commit references when available. Never place credentials or sensitive payloads in prompts or this log.
