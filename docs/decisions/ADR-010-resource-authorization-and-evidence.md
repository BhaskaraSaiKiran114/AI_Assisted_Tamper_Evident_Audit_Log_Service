# ADR-010 - Resource assignments and revision-bound quality evidence

Status: implementation authorized by user on 2026-10-28 after gap review. Human acceptance of final output and personal attestation remain pending. Supersedes service-wide authorization assumptions in ADR-006/009; historical milestone evidence remains historical.

## Decision and contract

The existing action permissions remain independent. A verified RS256 issuer also assigns either `audit_resources` (at most 100 exact resourceType/resourceId objects) or `audit_global: true`. Request parameters never assign resources. There are no wildcard semantics. A missing/empty assignment denies business calls; malformed claim types/entries invalidate authentication; nonempty scoped assignments combined with global=true are rejected as ambiguous. Issuers must authorize assignment changes and protect global grants. Tokens retain the existing expiry checks; there is no new online revocation service.

Example additional claims for a scoped reviewer/producer (the normal issuer/audience/subject/times and permissions are also required):

```json
{"audit_resources":[{"resourceType":"CLIENT_ACCOUNT","resourceId":"synthetic-account"}]}
```

Global operator additional claim: `{"audit_global":true}`. This grants no AUDIT_* permission by itself. Global tokens are deliberately privileged; only the trusted issuer may mint them. The development helper uses a fixed ACCOUNT/synthetic-resource assignment by default; `--global` explicitly selects local operator scope. This helper is not a production issuer or HTTP token endpoint.

| Operation | Assignment behavior (in addition to existing permission) |
| --- | --- |
| Append | Exact target must be assigned; reject 403 before encryption/persistence |
| List | SQL intersects assignments and user filters before pagination/decryption |
| Lookup | Return 404 for absent or unassigned ID, before payload/key access |
| Export | Include canonical content only for assigned matching records; other chain positions remain hash-only bridging headers |
| Access report | SQL intersects assigned CLIENT_ACCOUNT IDs before outcome processing/counting; optional account filters only narrow access |
| Redact | Check target assignment before mutation or operation-ID replay; unassigned targets return 404 |
| Verify / retention | Require global assignment because these operate on the shared chain/store |
| Archived reads/exports/reports | Existing AUDIT_LIFECYCLE permission is still required; archive opt-in never expands resource assignments |
| Documentation / health | Existing default policy and guarded local Swagger exception remain; no resource assignment needed |

Query/report cursors bind the subject, canonical sorted/deduplicated assignment set, global flag and all existing filters/limit. Another subject or changed assignments cannot reuse a cursor. Previously issued ordinary-query/report cursors are invalidated by this context change. A token refresh with the same subject/assignments can continue a snapshot.

Existing no-scope service overloads are trusted in-process entry points for internal workflows/tests, not HTTP adapters. HTTP adapters must pass `ResourceScope.from(authentication)`. Lower-level repositories and database credentials remain trusted application infrastructure, not independent user-authentication boundaries.

## Persistence and privacy consequences

No event bytes, hash inputs, migrations or export wire versions change. This is resource-level application authorization over one global chain, not independent tenant databases/chains or row-level security. ResourceType/resourceId form the assignment identity; callers with the same pair intentionally share access. Define collision-free issuer resource identifiers before shared deployment. JWT actorId remains a producer assertion.

Snapshot heads, sequences, previous hashes and export bridging hashes reveal shared-chain positions/activity. Scoped exports internally validate the whole bounded chain, so unrelated corruption can cause a generic failure. They provide no unauthorized canonical content or payload keys, but do not hide global activity or provide independently partitioned availability. Tenant-private proofs/heads require a separately approved versioned architecture. The existing 10,000-header export ceiling remains.

## Coverage and provenance

JaCoCo 0.8.14 instruments Surefire and Failsafe in the same clean build and produces XML/HTML reports at verify. Thresholds: bundle line 85%, branch 75%, method 85%; security package line 90%, branch 80%, method 90%. No application package exclusions. These are engineering regression gates, not an assignment-prescribed score. Required endpoint/risk cases are mapped in traceability; line coverage alone cannot establish their behavior. Reference: [official JaCoCo release history](https://www.jacoco.org/jacoco/trunk/doc/changes.html).

`scripts/build-evidence.py --begin` captures SHA-256 of non-ignored source/build/configuration/CI/script inputs before verification. Completion rejects changed inputs, missing/empty required suites, skipped/failed/error tests or missing coverage. The resulting JSON records base Git commit, branch, repository, dirty state, exact inputs, report hashes and actual GitHub run URL when present. A dirty build is never described as testing an unchanged committed HEAD. No self-referencing commit is inserted into the attestation; final submission references a separately retained validation artifact for the final reviewed commit.

CI runs PostgreSQL plus readiness, uploads reports in a SHA/run/attempt-named artifact, and runs a separate dependency audit that fails on feed errors. Artifact retention is 30 days, so the human must retain submission evidence outside that expiry. Configuration is not proof of a completed remote run. Candidate affirmation, repository privacy/access and final submission remain human-owned.
