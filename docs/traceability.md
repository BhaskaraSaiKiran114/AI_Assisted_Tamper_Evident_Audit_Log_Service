# Requirement traceability

Documentation coverage is separate from functional conformance. Evidence is in milestone ledgers 1-6 under validation/; planned later tests are not executed evidence.

| Requirement | Design evidence | Implementation / validation | State |
| --- | --- | --- | --- |
| A-01 | Architecture; encoding-v1; security contract | Strict ingestion plus real-JWT HTTP write/query on H2 and actual PostgreSQL runtime pool | Implemented/tested, AUDIT_WRITE enforced |
| A-02 | ADR-001/006; PostgreSQL V3 | No mutation route; actual runtime event UPDATE/DELETE/TRUNCATE/DDL denied 42501 | Implemented/tested; administrator bypass remains explicit |
| A-03, A-04 | Query contract; ADR-005 | Filters/cursors on H2/PostgreSQL plus actual secured runtime API | Implemented/tested, AUDIT_READ enforced |
| A-05 | ADR-002; encoding-v1 | V2, CanonicalJson, AuditAppender; golden vectors, 32 parallel appends and two rollback failure points on H2/PostgreSQL | M2 implemented/tested |
| A-06, A-07 | Query/security contracts; ADR-002/005 | Direct datastore mutation/deletion/head/link/schema and snapshot tests; actual JWT verify and bounded concurrency | Implemented/tested, AUDIT_VERIFY enforced; final defense M8 |
| B-01 | ADR-003/007; lifecycle contract | Ingestion-time boundary, batching, idempotency, hidden archives, concurrent intact verification and rollback on H2/PostgreSQL | M5 implemented/tested |
| B-02, B-03 | ADR-003/007; encoding-v1; lifecycle contract | Selective key erasure, unchanged ciphertext/hash, audited markers, replay/conflict, rollback after deletion, marker tampering/restored-key detection, real restricted-pool function | M5 implemented/tested; backup/WAL/snapshot limits explicit |
| B-04 | ADR-004/008; export contract | Protected sparse/archived/redacted bundles, signed membership/headers, source/transport tamper tests on H2/PostgreSQL and actual dependency-free CLI | M6 implemented/tested; hidden-content links rely on signer; completeness not proved |
| C-01 | Requirements clarification | Written before report code | M1 documentation delivered |
| C-02 | Report/access contract | Report tests and scope defense M7 | Planned |
| G-01 | Plan and authorization record | Separate user instructions for M1-M6; no M7 behavior | Authorized M6 scope respected |
| G-02 | History/submission policy | Actual M1 522fac7, M2 45229d1, M3 a4a7780, M4 7039dbb, M5 e3e5107 observed; M6 uncommitted; remote privacy/reviewer access unverified | Human repository action pending |
| G-03 | ATTESTATION.md; ignore rules | Identity/signature remain pending | Draft only |
| G-04, G-10 | AI log and progress ledger | M1-M6 generation/decisions/failure/validation entries | Records delivered; human review pending |
| G-05 | Requirements, architecture, plan, ADRs, threat model | Document review against source sections | M1 baseline delivered |
| G-06 | Testing strategy, POM and workflow | M6: 110 unit/H2 + 57 PostgreSQL + 1 offline-verifier cases, style/package gate; advisory/performance/remote CI pending | M6 evidence delivered |
| G-07 | Governance, security contract, ADR-006 | Pinned real JWTs, permission matrix, separate runtime pool, generic errors, ignored keys, no source copy | M4 security scope tested; production operations remain |
| G-08 | README, contracts and summary | Authenticated core API/OpenAPI and setup implemented; B scenarios implemented with stated limits; C reporting remains planned | Partial overall prototype |
| G-09 | POM, wrapper, profiles and M6 tests | Requested stack executes including real JWT/security/OpenAPI and PostgreSQL roles | M6 stack exercised; 17.6/remote deployment unexecuted |
| G-11 | ADRs, testing strategy, summary | Live defense/final readiness M8 | Planned |

M1-M6 complete: 168 tests (110 Surefire + 58 Failsafe: 57 PostgreSQL and one independent CLI), no failures/errors/skips, zero Checkstyle violations. Progress is 6/8 = 75% of milestones, not a functional-conformance percentage or reviewer score. M7-M8 remain unstarted. Normal verify runs 110 + the same one offline Failsafe test; do not double-count that test across runs. See ledgers for environment, proof and production-security boundaries.
