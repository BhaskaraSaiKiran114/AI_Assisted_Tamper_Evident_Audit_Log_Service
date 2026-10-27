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

## AI-003 - milestone 2 append-only ingestion

- Date: 2026-10-28, Asia/Calcutta. Tool: OpenAI Codex desktop assistant; no additional agents.
- Human prompt: "now implement Milestone 2". Authorization limited to M2; no M3 implementation started.
- Intent/criteria: validate bounded event input, freeze deterministic encoding and hash vectors, persist an append-only hash chain atomically, protect future-redactable payload values, prove concurrency/rollback on H2 and PostgreSQL, update evidence/governance.
- Generated: ingestion controller/service/parser, custom canonicalizer, field protection/key wrapping, transactional JDBC appender, V2 schema, shared reference tests, isolated Windows PostgreSQL runner, CI database configuration, encoding contract and milestone documentation.
- Accepted: database row locking and exact canonical bytes; AES-GCM protected values before hashing; external master key for persistent PostgreSQL; H2-only ephemeral key; keep runtime business access denied until M4.
- Edited/refined: corrected Spring ProblemDetail argument type; added full-envelope independent hash vector; changed Windows pg_ctl handling to explicit process handles/output files and bounded waits; require disposable database name/consent before reference connections; added manual development history reference to actual M1 commit 522fac7.
- Rejected: binary floating-point JSON normalization, plaintext-sensitive-value digests, plaintext field keys in the database, filesystem key writes requiring rollback cleanup, a permissive application security profile, treating H2-only concurrency as PostgreSQL proof, signing the candidate statement, or claiming query/verification/redaction complete.
- Tools/context: read repository governance/spec, inspected bundled Java/Jackson APIs locally using javap, used Python hashlib for independent vectors and Maven/JUnit for validation. Existing PostgreSQL binaries supplied a separate cluster; no external source searches or source-assignment upload required.
- Iterations/failures: compile type mismatch corrected; initial H2 run 26 passing cases; one extra golden vector then final suite passed 35. Initial PostgreSQL process handling stalled; cleanup diagnosed stale temporary PID state. A misidentified pg_ctl termination attempt was denied and stopped no process; the existing service remained running. Runner then successfully created/stopped its own isolated cluster. See M2 ledger for candid details.
- Validation: Java 21 `-Ppostgres-it clean verify` passed at 01:35:43; 27 Surefire + 8 PostgreSQL Failsafe tests, no failures/errors/skips, zero Checkstyle violations, executable JAR. PostgreSQL version tested: 16.3. A final pointer-escape guard refinement was covered by the existing configuration test and normal clean verify rerun (27 cases) passed at 01:40:36. CI/Compose 17.6 and vulnerability scan remain unexecuted. Optional Python YAML parsing was unavailable; no automated YAML-validation claim is made.
- Artifacts/traceability: A-01 ingestion and A-05 chaining; A-02 API mutation absence only (runtime database grants still M4); B-02/B-03 protected representation foundation, not redaction. See encoding contract, traceability and `docs/validation/milestone-2.md`.
- Human review/commit: acceptance pending; personal attestation unsigned; M2 change set is uncommitted at delivery. No fabricated commit or external publication.

## Entry template for subsequent milestones

Record date, human task, intent, constraints, acceptance criteria, context, AI/tool identity, generated/edited/rejected work and rationale, affected requirements/files, actual checks and failures, limitations, human review, and actual commit references when available. Never place credentials or sensitive payloads in prompts or this log.
