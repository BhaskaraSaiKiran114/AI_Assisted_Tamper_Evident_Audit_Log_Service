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

## AI-004 - milestone 3 query and chain verification

- Date: 2026-10-28, Asia/Calcutta. Tool: OpenAI Codex desktop assistant; no additional agents used.
- Human prompt: "now implement Milestone 3". Authorization limited to M3; no M4 JWT/permission implementation started.
- Intent/criteria: implement combined filters, bounded stable pagination, identity lookup, full-chain verification with first violation, direct tamper evidence and concurrent snapshot checks on H2/PostgreSQL; preserve deny-all HTTP staging and update governance.
- Generated: read repository/domain records, filter parser, HMAC cursor codec, row integrity checker, payload restoration, read snapshot transaction boundary, query/verification services/controllers, safe operational errors, shared tests, ADR-005, query contract and M3 evidence.
- Accepted: strict parameterized AND filters and [from,to); sequence keysets with signed filter/limit/upper-head context; compare all query columns to committed canonical bytes; verify all rows rather than only rows under recorded head; distinguish missing keys from broken ciphertext commitments; do not invent event IDs for missing records.
- Modified/refined: wildcard-map assertion corrected after compile error; H2 concurrency failure led to database-specific snapshot isolation (H2 SERIALIZABLE / PostgreSQL REPEATABLE_READ) in separate read-only transactions, with append transactions unchanged. Added unknown-schema and sparse-pagination coverage. Removed an arbitrary stored-document size cap that could reject valid expanded ciphertext/decimals; ingress/cursor bounds and parser constraints remain.
- Rejected: offset paging, unsigned client-controlled snapshot bounds, reusing the payload master key for cursors, treating filtered pages as whole-chain proof, blocking all writes during verification, weakening/skipping the concurrency test, treating operational errors as intact=true, interpreting a missing key as approved redaction, or enabling public business access before M4.
- Tools/context: read repository governance/spec/code/history; local Java/Maven/JUnit/JDBC tooling; official H2 and PostgreSQL isolation documentation consulted for an observed test failure. Public queries contained no confidential assignment text, client data or secrets. Isolated PostgreSQL runner reused without existing-service operations.
- Validation/iterations: initial test compile failure fixed; first H2 execution 46 cases/1 snapshot failure; corrected H2 run 47 passed; final Java 21 offline `-Ppostgres-it clean verify` passed at 02:11:49 in 50.542 seconds: 47 Surefire + 24 PostgreSQL Failsafe tests, zero failures/errors/skips, zero Checkstyle violations and executable JAR. Temporary database stopped normally. See M3 ledger for commands and precise coverage.
- Artifact/requirement linkage: A-03/A-04 combined query/paging, A-06/A-07 verification/direct tampering; query/verification contract and ADR-005 describe limits. No archive/redaction/export/regulatory implementation is claimed.
- Repository/human review: actual prior commits 522fac7 (M1), 45229d1 (M2) observed; M3 change set uncommitted at delivery. Human acceptance pending; candidate statement unsigned; no external publication or fabricated history.
- Remaining checks/limits: local PostgreSQL 16.3 only; 17.6/remote CI, security audit, performance/SLO and runtime role separation pending. Process-random cursor keys invalidate cursors after restart unless separately configured. Query restores protected values, but verification independently checks stored ciphertext. Full-store coherent rewrite requires external evidence to detect.

## AI-005 - milestone 4 security and API contract

- Date: 2026-10-28, Asia/Calcutta. Tool: OpenAI Codex desktop assistant; no additional agents.
- Human prompt: "now implement Milestone 4". Authorization limited to M4; no M5 lifecycle implementation started.
- Intent/criteria: real JWT issuer/audience/signature/time validation and permission matrix, safe auth errors, protected OpenAPI/Swagger, separate migration/runtime privileges and proof with the actual runtime pool; update documentation/evidence.
- Generated: JwtConfiguration, SecurityProblems and route policy; explicit OpenAPI schemas/permission metadata; local token source launcher; verification concurrency guard; fresh-cluster bootstrap, PostgreSQL V3 grants/config; signed-token and runtime-role tests; security contract, ADR-006 and M4 ledger.
- Accepted: pinned RS256 RSA >=2048, header-only stateless authentication, exact issuer/audience, required subject/time claims, no implicit role/scope inheritance; separate DOCS permission; no runtime event mutation/DDL/key deletion or migrator-role assumption; loopback-only disposable role tests with an additional consent flag.
- Modified/refined: replaced M1-M3 unconditional deny staging with validated JWT permissions; unconfigured H2 decoder still rejects everything. Added explicit permission mappings so new API operations cannot silently inherit documentation authority. Added a per-process verification scan budget before exposing that work. Kept actual destructive fixtures on separate admin connections while testing business behavior with the real restricted pool. Local key files require owner-only permissions and are never overwritten.
- Rejected: a home-grown server identity provider/login endpoint, trusting token-provided alg/jku as configuration, fallback to unsigned/HMAC tokens, roles/scope prefix escalation, public Swagger/spec assets, query/cookie token shortcuts, schema-owner runtime credentials, premature key-deletion grants, silently upgrading/resetting existing databases/volumes or inventing candidate approval/signature.
- Tools/sources: repository governance/spec/code/history, local Java/JUnit/Maven/JDBC; generic public Spring Security JWT and PostgreSQL privileges documentation checks. No confidential assignment, actual token, private key or real client payload uploaded to external sources. Tests use generated in-memory/ignored local keys and synthetic payloads.
- Iterations: baseline 47 passed after wiring; new test compile variable-shadowing fixed; first expanded 63-case run had one developer-helper failure, traced to wrong POSIX permission API usage and corrected without weakening permissions. Focused helper/weak-key tests passed; final combined Java 21 offline -Ppostgres-it clean verify passed at 08:37:14 (reported 01:11 min): 63 Surefire + 26 PostgreSQL cases, no failures/errors/skips, zero Checkstyle violations, executable JAR and normal temporary-cluster stop.
- Evidence/traceability: A-02 database append-only privileges, G-06/G-07 security and oversight, G-08/G-09 runnable authenticated API/schema stack. Runtime tests prove eleven SQL privilege denials and separate migration ownership. A final API schema review made receipt/error responses explicit and strengthened document assertions; normal clean verify passed all 63 cases at 08:50:54 without rerunning unchanged database behavior. Reference evidence remains the prior 26-case combined execution. See security-and-api.md, ADR-006 and validation/milestone-4.md.
- Review/commits: observed actual M1 522fac7, M2 45229d1, M3 a4a7780; M4 changes uncommitted at delivery. Human output acceptance pending and attestation unsigned. No remote CI/publication, reviewer access grant, signature or fabricated history.
- Remaining limits: local PostgreSQL 16.3 only; Compose/CI 17.6 and psql-only fresh-volume password initialization unexecuted; dependency scan/benchmark later. Startup Flyway secrets remain in-process; production migration-job separation, key operations and token lifecycle are not certified. Protected Swagger UI needs a header-capable browser/client. Future lifecycle/export/compliance routes remain denied.

## Entry template for subsequent milestones

Record date, human task, intent, constraints, acceptance criteria, context, AI/tool identity, generated/edited/rejected work and rationale, affected requirements/files, actual checks and failures, limitations, human review, and actual commit references when available. Never place credentials or sensitive payloads in prompts or this log.
