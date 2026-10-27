# Requirement traceability

Documentation coverage is separate from functional conformance. Evidence is in `validation/milestone-1.md`, `validation/milestone-2.md` and `validation/milestone-3.md`; planned later tests are not executed evidence.

| Requirement | Design evidence | Implementation / validation | State |
| --- | --- | --- | --- |
| A-01 | Architecture; encoding-v1 | EventParser, AuditWriteController, AuditIngestionService and shared H2/PostgreSQL append tests | Ingestion contract implemented/tested; HTTP stays denied until M4 |
| A-02 | Architecture; ADR-001 | No PUT/PATCH/DELETE handler; standalone adapter and app deny-all tests | API mutation absence implemented; restricted runtime DB grants remain M4 |
| A-03, A-04 | Query/verification contract; ADR-005 | EventQuery, QueryCursorCodec, AuditQueryService/repository/controller; filter/time/sparse-page/signature/anchor tests on H2/PostgreSQL | M3 implemented/tested; HTTP staged until M4 |
| A-05 | ADR-002; encoding-v1 | V2, CanonicalJson, AuditAppender; golden vectors, 32 parallel appends and two rollback failure points on H2/PostgreSQL | M2 implemented/tested |
| A-06, A-07 | Query/verification contract; ADR-002/005; threat model | ChainVerificationService, row checker and ReadSnapshot; direct SQL mutation/deletion/head/link/schema tests and concurrent snapshot on H2/PostgreSQL | M3 implemented/tested; integrated JWT walkthrough remains M4/M8 |
| B-01 | ADR-003 | Archival/intact-chain tests M5 | Planned |
| B-02, B-03 | ADR-003; encoding-v1 | PayloadProtector, wrapped-key persistence and encryption/AAD tests | Protected representation implemented; actual redaction/erasure remains M5 |
| B-04 | ADR-004 | Independent bundle/tamper tests M6 | Planned |
| C-01 | Requirements clarification | Written before report code | M1 documentation delivered |
| C-02 | Report/access contract | Report tests and scope defense M7 | Planned |
| G-01 | Plan and authorization record | Separate user instructions for M1-M3; no M4 behavior | Authorized M3 scope respected |
| G-02 | History/submission policy | Actual M1 522fac7 and M2 45229d1 commits observed; M3 changes uncommitted; remote privacy/reviewer access unverified | Human repository action pending |
| G-03 | ATTESTATION.md; ignore rules | Identity/signature remain pending | Draft only |
| G-04, G-10 | AI log and progress ledger | M1-M3 generation/decisions/failure/validation entries | Records delivered; human review pending |
| G-05 | Requirements, architecture, plan, ADRs, threat model | Document review against source sections | M1 baseline delivered |
| G-06 | Testing strategy, POM and workflow | M3: 47 unit/H2 + 24 PostgreSQL tests and style gate; advisory/performance/remote CI pending | M3 evidence delivered |
| G-07 | Governance and AGENTS.md | Deny-all policy, ignored secrets, no source copy | M1 safeguards; full security M4 |
| G-08 | README, architecture, summary | Foundation/ingestion/read/verify implemented; authentication and remaining B/C behavior planned | Partial prototype |
| G-09 | POM, wrapper and profiles | M1 build/toolchain ledger; JWT/OpenAPI behavior M4 | Dependencies/configuration only |
| G-11 | ADRs, testing strategy, summary | Live defense/final readiness M8 | Planned |

M1-M3 are complete: 71 tests (47 Surefire + 24 PostgreSQL Failsafe), no failures/errors/skips, zero Checkstyle violations. Progress is 3/8 = 37.5% of milestones, not a functional-conformance percentage or reviewer score. M4-M8 remain unstarted. See the validation ledgers for database-version, archive/read and security boundaries.
