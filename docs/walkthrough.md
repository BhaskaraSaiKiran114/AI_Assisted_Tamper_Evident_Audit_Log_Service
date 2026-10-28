# Prototype walkthrough and submission preparation

Run on Java 21 from the project root. Use synthetic records and disposable stores. The source assignment, real customer data, credentials, private keys and local bundles are excluded from Git. Human acceptance and submission are separate from automated checks.

## Reproduce the final automated walkthrough

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\scripts\verify-postgres.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\16\bin' -Readiness
```

The Windows runner creates a new GUID-named cluster under ignored .tools, enables loopback only, supplies generated test credentials and guarded role/database consent, bootstraps separate runtime/migration users, runs postgres-it,readiness clean verify and stops its own cluster. It does not reuse an existing service or delete a useful database. Optional -MavenRepository/-Offline use an already populated cache; -Port chooses another unused port. Installed PostgreSQL binaries are required. Ordinary CI/reference verification omits the opt-in performance exercise unless readiness is selected explicitly.

The three readiness cases use real HTTP to an embedded server on a dynamic loopback port with actual restricted PostgreSQL runtime credentials:

1. Scenario A: authenticated ingestion and combined query, missing/wrong permission checks, denied API/SQL mutation, intact verification, then a guarded administrator fixture changes a searchable field and verification reports INDEX_FIELD_MISMATCH. Export refuses the corrupted source.
2. Scenario B: erase a synthetic protected field through its explicit acknowledged operation, replay safely, prove original canonical bytes unchanged, archive a separately seeded aged record, enforce archive read opt-in, export selected ciphertext plus proof, and run the packaged standalone verifier against that actual HTTP bundle. The old fixture is inserted through AuditAppender solely to control server-ingestion age; no timestamp on an existing event is rewritten. Lifecycle/export use the fixture clock defined in LifecycleTestClock, not a changed system clock.
3. Scenario C: a COMPLIANCE-only token retrieves account/actor/action/outcome/time metadata with no payload. It relies on the documented CLIENT_ACCOUNT/plaintext-outcome vocabulary, not an inferred legal policy.

One case also starts the packaged Boot JAR in a separate Java process with a fresh H2 store, dynamic loopback port and generated public JWT verification key, then checks migration, public health, authenticated write/verify, missing-token denial and absent-export-key failure. The child process is stopped in finally. This establishes artifact startup separately from classpath tests; it is not a Docker/remote deployment claim.

## Inspect the local results

target/readiness contains scalar performance.json, synthetic scenario-export.zip, trusted public-key file, verifier result, scenario result and packaged-start logs. All are ignored; no private key is written by the exercise. Preserve measurements outside target before another clean if needed. The final raw outputs were copied to ignored .tools/m8-readiness-final (earlier pre-patch/patched baselines remain .tools/m8-readiness and .tools/m8-readiness-patched), while sanitized final metrics are in docs/validation/milestone-8-performance.json. Surefire/Failsafe XML and local build logs supply test evidence; a configured CI workflow is not an executed remote run.

## Manual API exploration

Follow README and security-and-api.md to start H2 or a separately provisioned PostgreSQL database and initialize .tools/jwt once. Mint only the short-lived permissions needed for each action. The contract guides contain complete request shapes:

- encoding-v1.md: POST /audit/events; caller occurrence time versus server ingestion time, canonical bytes and field protection.
- query-verification.md: combined query/cursor continuation, identity lookup and full-chain verification.
- lifecycle.md: retention operationId/reason, explicit redaction approvalReference/confirmation/paths, replay/conflict and archive opt-in.
- exports.md: separate export key pair, bounded signed bundle and standalone Java verification with an independently trusted public key.
- access-report.md: mandatory [from,to), declared action/outcome vocabulary, exact filters, excluded-outcome counts and metadata-only output.

OpenAPI/Swagger requires DOCS for HTML/config/assets/spec and the relevant business permission for executed calls. Use a header-capable client/browser. Ordinary navigation without a token is expected to return 401; there is no login/token URL bypass.

For a direct tamper demonstration, use only the runner's guarded synthetic fixture or the automated exercise. Runtime SQL mutation is denied. The administrator is deliberately stronger and can corrupt history; same-database hashes do not prevent a coherent privileged rewrite. Whole-database rollback defense requires independently retained evidence/checkpoints outside this prototype.

## Live-change rehearsal

Explain and locate the implementation before making a reviewer-requested change: strict input/canonicalization in audit, SQL/transactions in persistence, policies in security/API, evidence in lifecycle, signed binary proof in export and metadata projection in report. Identify the affected requirement, compatibility/version boundary and test contract. Make the smallest change, run the relevant test then clean verify/reference verification as appropriate, and update AI usage/evidence. Do not rewrite an applied migration or change frozen hash/cursor/bundle semantics silently. This is a written rehearsal procedure, not a claim of a live human defense.

## Human-owned completion

Review the diff, actual validation ledger, security findings and residual risks. Confirm the intended interpretation of Scenario C and prototype limits. Supply and affirm your own identity/start/submission dates and attestation; the assistant does not sign or invent acceptance. Review authentic commits (M1-M7 observed; M8 is uncommitted at delivery), repository privacy, intended reviewer and access permissions. Private repository creation/push/sharing/access grants and submission require explicit user action/authorization. No remote state or reviewer access is claimed verified here.

Before deployment, resolve any release holds from the security review, validate the intended PostgreSQL patch/image in your environment, separate migration-job secrets from runtime, operate signing/payload keys and backup/privacy controls, measure the actual workload and establish tenant/reviewer scope. These are production concerns beyond the demonstrated local prototype.
