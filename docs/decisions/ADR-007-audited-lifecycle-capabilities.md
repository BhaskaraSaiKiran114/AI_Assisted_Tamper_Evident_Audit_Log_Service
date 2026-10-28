# ADR-007: Atomic lifecycle evidence and scoped key erasure

Date: 2026-10-28. Status: implemented under M5 authorization.

Decision: logical archive by server ingestion age, strict cutoff and bounded batches, preserving chain material. Retain administrative evidence indefinitely. Redact only values protected at ingestion by removing wrapped field keys while retaining ciphertext/hash. Require selected paths, reason, confirmation and approval reference; derive operator from JWT.

Serialize lifecycle and normal append writes with the chain-head row lock. Commit administrative event, proof rows, markers, head and key erasure atomically. Bind operation UUID to canonical operator/intent/policy: exact retries replay; changed intent conflicts; concurrent duplicates yield one event.

Keep direct runtime key DELETE denied. PostgreSQL V5 exposes one SECURITY DEFINER function requiring matching proof and deleting one exact event/path. Qualify objects, put pg_temp last in a restricted search_path and revoke PUBLIC execute. H2 retains service-level transaction semantics without role claims. SQL does not certify external approval or prevent compromised credentials from manufacturing future allowed inserts.

Reads validate proof before displaying erasure markers; unexplained key loss remains an error. Restored keys under a marker fail integrity checks rather than reveal values. Archived reads require READ plus LIFECYCLE; cursor v2 binds archive mode. Verification includes all immutable events and checks action/marker/key consistency, returning LIFECYCLE_MISMATCH.

Alternatives: mutating/deleting event rows breaks integrity; broad key DELETE weakens M4; filesystem keys complicate rollback; treating every missing key as approved redaction hides corruption; plaintext sensitive digests enable guessing. A separate lifecycle account could isolate operational credentials further, at the cost of another pool/credential boundary. The scoped function retains the existing pool with explicit evidence.

Consequences: shared-lock serialization and additional proof queries impose costs; administrative text persists; active-store erasure does not cover WAL/backups/MVCC/caches/replicas/in-flight reads/exports. No KMS, compliance certification, scheduler, purge or production readiness is implied. See lifecycle.md and M5 evidence for protocol and validation.
