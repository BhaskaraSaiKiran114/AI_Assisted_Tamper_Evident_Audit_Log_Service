# Requirement traceability

Documentation coverage is separate from functional conformance. Evidence is in milestone ledgers 1-8 under validation/; planned later tests are not executed evidence.

| Requirement | Design evidence | Implementation / validation | State |
| --- | --- | --- | --- |
| A-01 | Architecture; encoding-v1; security contract | Strict ingestion plus real-JWT HTTP write/query on H2 and actual PostgreSQL runtime pool | Implemented/tested, AUDIT_WRITE enforced |
| A-02 | ADR-001/006; PostgreSQL V3 | No mutation route; actual runtime event UPDATE/DELETE/TRUNCATE/DDL denied 42501 | Implemented/tested; administrator bypass remains explicit |
| A-03, A-04 | Query contract; ADR-005 | Filters/cursors on H2/PostgreSQL plus actual secured runtime API | Implemented/tested, AUDIT_READ enforced |
| A-05 | ADR-002; encoding-v1 | V2, CanonicalJson, AuditAppender; golden vectors, 32 parallel appends and two rollback failure points on H2/PostgreSQL | M2 implemented/tested |
| A-06, A-07 | Query/security contracts; ADR-002/005 | Direct datastore mutation/deletion/head/link/schema and snapshot tests; actual JWT verify and bounded concurrency | Implemented/tested, AUDIT_VERIFY enforced; M8 real HTTP/SQL tamper demonstration passed |
| B-01 | ADR-003/007; lifecycle contract | Ingestion-time boundary, batching, idempotency, hidden archives, concurrent intact verification and rollback on H2/PostgreSQL | M5 implemented/tested |
| B-02, B-03 | ADR-003/007; encoding-v1; lifecycle contract | Selective key erasure, unchanged ciphertext/hash, audited markers, replay/conflict, rollback after deletion, marker tampering/restored-key detection, real restricted-pool function | M5 implemented/tested; backup/WAL/snapshot limits explicit |
| B-04 | ADR-004/008; export contract | Protected sparse/archived/redacted bundles, signed membership/headers, source/transport tamper tests on H2/PostgreSQL and actual dependency-free CLI | M6 implemented/tested; hidden-content links rely on signer; completeness not proved |
| C-01 | Requirements clarification | Written before report code | M1 documentation delivered |
| C-02 | Access-report contract; ADR-009 | Bounded metadata-only account/actor/time/action/outcome report; plaintext-vocabulary exclusions, archive permissions, signed cursors, no key reads and H2/PostgreSQL/runtime tests | M7 implemented/tested within explicit prototype scope; no legal certification or upstream-completeness proof |
| G-01 | Plan and authorization record | Separate user instructions for M1-M8 and M8 local readiness evidence | Authorized eight-milestone scope respected; no external submission |
| G-02 | History/submission policy | Actual M1 522fac7, M2 45229d1, M3 a4a7780, M4 7039dbb, M5 e3e5107, M6 86a1493, M7 085e332 observed; M8 uncommitted; remote privacy/reviewer access unverified | Human repository action pending |
| G-03 | ATTESTATION.md; ignore rules; ADR-010 | Candidate identity/email/submission date recorded; revision-bound evidence tooling added; personal affirmation and final reviewed submission revision pending | Unsigned candidate statement; no forged acceptance |
| G-04, G-10 | AI log and progress ledger | M1-M8 generation/decisions/failure/validation entries | Records delivered; human review pending |
| G-05 | Requirements, architecture, plan, ADRs, threat model | Document review against source sections | M1 baseline delivered |
| G-06 | Testing strategy, POM and workflow | M8: 210 readiness cases, style/package gate, advisory remediation/final pass and measured baseline; remote CI unexecuted | M8 evidence delivered with explicit scope/coverage limits |
| G-07 | Governance, security contract, ADR-006 | Pinned real JWTs, permission matrix, separate runtime pool, generic errors, ignored keys, no source copy | M4 security scope tested; production operations remain |
| G-08 | README, contracts and summary | Authenticated core API/OpenAPI and setup implemented; A/B and bounded C reporting implemented with explicit limits; local readiness/walkthrough delivered; human acceptance/submission pending | Partial overall prototype |
| G-09 | POM, wrapper, profiles and M8 evidence | Requested stack executes including real JWT/security/OpenAPI and PostgreSQL roles | M8 stack exercised; 17.6/remote deployment unexecuted |
| G-11 | ADRs, M8 ledger, walkthrough/summary | Local scenarios, written change rehearsal, gates and measured baseline delivered | Human live defense/acceptance pending; no fabricated review |

All eight engineering deliverables delivered: final readiness 210 checks (132 Surefire + 78 Failsafe: 74 PostgreSQL, one CLI, three readiness), zero failures/errors/skips and zero Checkstyle violations. Advisory gate passed with engine 13.0.0 in stated NVD/RetireJS scope; OSS Index lacked credentials. Local measurements and all limitations are recorded. Progress 8/8=100% means milestone implementation delivery, not full requirement acceptance, reviewer accuracy or production certification. Normal/reference counts remain 133/207; repeated tests are not added. Human-owned attestation/remote access/live defense/acceptance/submission are still pending.

## Approved gap remediation mapping

| Requirement | Implementation and risk evidence | Acceptance boundary |
| --- | --- | --- |
| SEC-03 / TEST-04 | ResourceScope, JWT validation, scoped controllers/services, SQL query/report predicates, authorized export selection and pre-replay redaction checks; ResourceAuthorizationContract on H2/audit_runtime and ResourceScopeTest | Exact resource pairs; trusted issuer/global operators; shared-chain metadata remains visible |
| TEST-09 | Maven JaCoCo combined unit/IT XML/HTML plus enforced bundle/security line/branch/method thresholds | Measured structural coverage, not a percentage of functional correctness |
| CI-01 | PostgreSQL/readiness required-suite guard, no failures/errors/skips, dependency-audit job, SHA/run/attempt artifact upload; six Python evidence-gate regression tests | Configured remote workflow is distinct from an executed remote result |
| ATT-01/02/05 | Candidate identity retained; repository/branch/base revision, exact input manifest/report hashes and actual CI URL when available | Uncommitted sources labelled dirty; final human-reviewed revision/affirmation and external submission remain pending |

Final local follow-up passed 238 Java checks plus six Python evidence-gate cases, with bundle line/branch/method coverage 94.46%/79.92%/96.35% and all gates met. Executed results and failures are in [follow-up evidence](validation/functional-gap-remediation.md); original M1-M8 counts remain historical.
