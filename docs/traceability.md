# Requirement traceability

M1 documentation coverage is separate from functional conformance. Planned tests below are not executed evidence. Foundation results are in `validation/milestone-1.md`.

| Requirement | Design evidence | Implementation / validation | State |
| --- | --- | --- | --- |
| A-01, A-02 | Architecture append contract; ADR-001 | Schema/write API and runtime grants M2/M4 | Planned |
| A-03, A-04 | Query/cursor contract | Combined filter/cursor tests M3 | Planned |
| A-05 | ADR-002; genesis contract | M1 migration/test covers head only; chain tests M2 | Foundation only |
| A-06, A-07 | Verification design; threat model | Direct mutation/full verification M3/M8 | Planned |
| B-01 | ADR-003 | Archival/intact-chain tests M5 | Planned |
| B-02, B-03 | ADR-003; privacy boundary | Protected representation M2, key-erasure tests M5 | Design recorded |
| B-04 | ADR-004 | Independent bundle/tamper tests M6 | Planned |
| C-01 | Requirements clarification | Written before report code | M1 documentation delivered |
| C-02 | Report/access contract | Report tests and scope defense M7 | Planned |
| G-01 | Plan and authorization record | User instruction for M1; no later behavior | M1 scope respected |
| G-02 | History/submission policy | Initial commit exists; remote privacy/reviewer access unverified; M1 changes not committed | Human repository action pending |
| G-03 | ATTESTATION.md; ignore rules | Identity/signature remain pending | Draft only |
| G-04, G-10 | AI log and progress ledger | M1 generation/decisions/validation entry | Records delivered; human review pending |
| G-05 | Requirements, architecture, plan, ADRs, threat model | Document review against source sections | M1 baseline delivered |
| G-06 | Testing strategy, POM and workflow | M1 ledger; later PostgreSQL/security/performance evidence | Foundation gates only |
| G-07 | Governance and AGENTS.md | Deny-all policy, ignored secrets, no source copy | M1 safeguards; full security M4 |
| G-08 | README, architecture, summary | Bootstrap only; A/B/C prototype not complete | Partial foundation |
| G-09 | POM, wrapper and profiles | M1 build/toolchain ledger; JWT/OpenAPI behavior M4 | Dependencies/configuration only |
| G-11 | ADRs, testing strategy, summary | Live defense/final readiness M8 | Planned |

M1 is verified: Java 21 clean verify passed, 3 tests passed and Checkstyle reported zero violations. See the validation ledger for boundaries. Progress is 1/8 = 12.5% of milestones; this is not functional-conformance percentage or a reviewer score. M2-M8 remain unstarted.
