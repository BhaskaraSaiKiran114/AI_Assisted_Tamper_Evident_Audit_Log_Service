# Requirement traceability

Documentation coverage is separate from functional conformance. Evidence is in milestone ledgers 1-5 under validation/; planned later tests are not executed evidence.

| Requirement | Design evidence | Implementation / validation | State |
| --- | --- | --- | --- |
| A-01 | Architecture; encoding-v1; security contract | Strict ingestion plus real-JWT HTTP write/query on H2 and actual PostgreSQL runtime pool | Implemented/tested, AUDIT_WRITE enforced |
| A-02 | ADR-001/006; PostgreSQL V3 | No mutation route; actual runtime event UPDATE/DELETE/TRUNCATE/DDL denied 42501 | Implemented/tested; administrator bypass remains explicit |
| A-03, A-04 | Query contract; ADR-005 | Filters/cursors on H2/PostgreSQL plus actual secured runtime API | Implemented/tested, AUDIT_READ enforced |
| A-05 | ADR-002; encoding-v1 | V2, CanonicalJson, AuditAppender; golden vectors, 32 parallel appends and two rollback failure points on H2/PostgreSQL | M2 implemented/tested |
| A-06, A-07 | Query/security contracts; ADR-002/005 | Direct datastore mutation/deletion/head/link/schema and snapshot tests; actual JWT verify and bounded concurrency | Implemented/tested, AUDIT_VERIFY enforced; final defense M8 |
| B-01 | ADR-003/007; lifecycle contract | Ingestion-time boundary, batching, idempotency, hidden archives, concurrent intact verification and rollback on H2/PostgreSQL | M5 implemented/tested |
| B-02, B-03 | ADR-003/007; encoding-v1; lifecycle contract | Selective key erasure, unchanged ciphertext/hash, audited markers, replay/conflict, rollback after deletion, marker tampering/restored-key detection, real restricted-pool function | M5 implemented/tested; backup/WAL/snapshot limits explicit |
| B-04 | ADR-004 | Independent bundle/tamper tests M6 | Planned |
| C-01 | Requirements clarification | Written before report code | M1 documentation delivered |
| C-02 | Report/access contract | Report tests and scope defense M7 | Planned |
| G-01 | Plan and authorization record | Separate user instructions for M1-M5; no M6 behavior | Authorized M5 scope respected |
| G-02 | History/submission policy | Actual M1 522fac7, M2 45229d1, M3 a4a7780, M4 7039dbb observed; M5 uncommitted; remote privacy/reviewer access unverified | Human repository action pending |
| G-03 | ATTESTATION.md; ignore rules | Identity/signature remain pending | Draft only |
| G-04, G-10 | AI log and progress ledger | M1-M5 generation/decisions/failure/validation entries | Records delivered; human review pending |
| G-05 | Requirements, architecture, plan, ADRs, threat model | Document review against source sections | M1 baseline delivered |
| G-06 | Testing strategy, POM and workflow | M5: 85 normal + 41 PostgreSQL cases, style/package gate; advisory/performance/remote CI pending | M5 evidence delivered |
| G-07 | Governance, security contract, ADR-006 | Pinned real JWTs, permission matrix, separate runtime pool, generic errors, ignored keys, no source copy | M4 security scope tested; production operations remain |
| G-08 | README, contracts and summary | Authenticated core API/OpenAPI and setup implemented; remaining B/C behavior planned | Partial overall prototype |
| G-09 | POM, wrapper, profiles and M4 tests | Requested stack executes including real JWT/security/OpenAPI and PostgreSQL roles | M5 stack exercised; 17.6/remote deployment unexecuted |
| G-11 | ADRs, testing strategy, summary | Live defense/final readiness M8 | Planned |

M1-M5 complete: 126 tests (85 Surefire + 41 PostgreSQL Failsafe), no failures/errors/skips, zero Checkstyle violations. Progress is 5/8 = 62.5% of milestones, not a functional-conformance percentage or reviewer score. M6-M8 remain unstarted. See ledgers for version, lifecycle and production-security boundaries.
