# Retention and structured redaction - M5

M5 adds authenticated manual lifecycle commands. They change lifecycle metadata/field keys and append administrative evidence; existing event content, ciphertext, canonical bytes and hashes stay unchanged. No event update/delete API, scheduler or physical event purge is introduced.

## Retention

POST /audit/retention/runs requires AUDIT_LIFECYCLE and exactly:

```json
{"operationId":"00000000-0000-0000-0000-000000000001","reason":"synthetic-policy-run"}
```

Configuration: AUDIT_RETENTION_WINDOW (ISO-8601 duration, default P90D), AUDIT_RETENTION_POLICY_VERSION (default v1), AUDIT_RETENTION_BATCH_SIZE (default 100). The window is positive whole seconds, <=36500 days; policy version nonblank/<=64 UTF-16 units; batch size 1-500.

The server captures UTC execution time at microsecond precision. Eligible business events have ingested_at strictly before now-window and no existing archive marker. Equality is excluded. Caller occurrence time does not determine retention age. AUDIT_INTERNAL_ administrative evidence is excluded from eligibility and retained indefinitely. Process oldest sequences, at most one batch; hasMore indicates another batch is needed with a new operation ID. A zero-target batch still audits the policy attempt.

Archival is logical: it preserves immutable material and field keys, does not reclaim storage, and never removes records from full verification. Policy/version/window, cutoff, operator, targets, time, reason and outcome are committed in an administrative event.

## Selective key erasure

POST /audit/events/{eventId}/redactions requires AUDIT_LIFECYCLE and exactly:

```json
{
  "operationId":"00000000-0000-0000-0000-000000000002",
  "reason":"synthetic-privacy-request",
  "approvalReference":"synthetic-approved-change",
  "confirmation":"ERASE_FIELD_KEYS",
  "paths":["/accountNumber"]
}
```

Require 1-20 unique RFC 6901 pointers (<=1024 characters each), exact erasure confirmation and nonblank reason/approval reference <=255 UTF-16 units. Sort paths for request identity; ~0/~1 escape tilde/slash and numeric segments address arrays. Both command bodies are bounded to 16 KiB and reject extra/duplicate fields, trailing JSON and malformed UUIDs. Operator is the validated JWT subject; a supplied actor field is rejected.

Every requested path must be a protected envelope actually stored at ingestion, independent of the current protected-path configuration. Validate all paths before mutation. Unprotected fields and internal lifecycle evidence cannot be redacted this way. A missing key without prior validated redaction evidence is 503, never legitimized as approved erasure. Authenticate ciphertext/key bindings before deleting a newly selected wrapped-key row. Already redacted fields cause no further key erasure; a new operation ID may audit a zero-new-field attempt.

approvalReference records the operator's claimed approval reference; there is no external ticket validation or certification of human approval. Use non-sensitive reason/reference text: retained administrative evidence intentionally avoids erasable business-field encryption so verification stays key-independent. The implementation request authorizes code/tests, not erasure of meaningful user data.

## Atomicity, receipts and retries

Lifecycle commands acquire the chain-head row lock in one 30-second transaction, serializing with appends and other lifecycle writes. Administrative event, head advance, lifecycle_action, markers and key deletion commit together. Any exception rolls all back, including restoring an uncommitted deleted key. No filesystem key operation needs compensation.

operationId is shared across lifecycle command kinds. Store SHA-256 over ASCII AUDIT-LIFECYCLE-REQUEST-V1, NUL and canonical normalized intent: action/operator/reason plus effective retention policy/window/batch or redaction target/paths/approval. Exact retries return the original receipt without another event; changed intent/operator/policy under the same ID returns 409. Original cutoff/results remain fixed on replay. Concurrent duplicates yield one event. Ordinary ingestion idempotency is unchanged.

Return 200 with operationId, action, auditEventId, affectedCount, completedAt, cutoff/policyVersion, hasMore, affectedEventIds and affectedPaths. Retention count means archived records; redaction count means newly erased keys. Redaction identifies its target even when affectedCount=0. Invalid requests are 400/413, missing target 404, conflict 409, and storage/integrity/key failure 503; authentication remains 401/403.

## Query behavior

Default query/lookup hides archived events; lookup returns 404. includeArchived=true requires both AUDIT_READ and AUDIT_LIFECYCLE, with strict true/false syntax. Responses add archived and redactedPaths. Cursor version 2 binds archive mode and rejects old v1 cursors. Keep all filters/limit/mode on continuation. Later appends remain excluded; later lifecycle changes may remove records or replace values on subsequent page snapshots. There is no historical privacy snapshot across requests.

Validated erasure returns this payload marker; top-level redactedPaths is authoritative:

```json
{"$auditRedacted":{"operationId":"00000000-0000-0000-0000-000000000002","redactedAt":"2026-10-28T12:00:00Z"}}
```

New business input reserves $auditProtected/$auditRedacted keys and AUDIT_INTERNAL_ event types. Marker-like values in arbitrary historical plaintext do not generate authoritative redactedPaths. Existing deployments must review reserved namespace collisions before upgrade; no prior event is rewritten or rehashed automatically. Missing keys remain 503 without matching evidence. Restoring a key under an existing erasure marker is an integrity failure, preventing plaintext disclosure. Archived targets can also be redacted; archive access restrictions remain in force.

## Evidence and verification

V4 adds lifecycle_action, event_archive and payload_redaction. Each action references a unique immutable administrative event. Internal types are AUDIT_INTERNAL_RETENTION/REDACTION and resource type AUDIT_LIFECYCLE. Their payload commits operation/request identity, operator, reason/approval, policy/cutoff, targets/paths, time and result. Ordinary WRITE callers cannot produce that namespace.

Full verification still traverses all records including archived content, then cross-checks action data and markers against committed administrative payloads, times, archive target eligibility, protected paths and absence of erased keys. Missing/changed/extra markers, missing actions or restored keys report LIFECYCLE_MISMATCH at the administrative or wrongly associated event. Earlier content/sequence/link violations retain precedence. Unexplained key unavailability outside recorded erasure remains separate from ciphertext-chain integrity.

## PostgreSQL boundary

PostgreSQL V5 grants runtime SELECT/INSERT on the new evidence tables and EXECUTE on public.erase_audit_field_key(uuid,text,uuid). Direct DELETE on keys/events/head/proof remains denied. The SECURITY DEFINER function runs as the schema owner, qualifies relations, fixes search_path to pg_catalog,public,pg_temp, revokes PUBLIC execute, and erases only one exact event/path when matching redaction/action/administrative evidence is visible in that transaction. There is no dynamic SQL or table-wide delete capability. Flyway creates/restricts the function in one transaction. [PostgreSQL guidance](https://www.postgresql.org/docs/16/sql-createfunction.html#SQL-CREATEFUNCTION-SECURITY) informed those restrictions.

JWT policy is an application boundary: compromised runtime credentials can manufacture future inserts/evidence within their capabilities; SQL does not validate JWTs or external approvals. This preserves narrow deletion capability and an operation trail, not immunity to process/credential compromise. H2 uses local transactional key DELETE and makes no database-role-hardening claim.

## Privacy and operational limits

Erasure removes a wrapped field key from the active transactional store. WAL, backups, replicas, old MVCC snapshots, caches, in-flight reads and prior exports may retain recoverable copies. The persistent master key can unwrap an old wrapped-key copy. Real privacy operations need reviewed key/backup/replica retention, snapshot retirement and purge procedures. No irreversible physical erasure or legal-compliance certification is claimed. Unprotected fields, metadata and administrative text remain outside selective erasure.

Reads/verifications begun before lifecycle commit may finish against their earlier snapshot. Erasure does not retract plaintext already delivered or held in memory. Archival retains storage. Per-query proof validation may repeat across targets; M8 measured default batch 100/page 50 in performance.md, exposing repeated proof cost; maximum batch/page and larger fanout still require production measurement/tuning. Full verification adds metadata/target checks; transaction timeouts are not CPU/wall-clock SLAs.

No automatic scheduler, legal hold, tenant policy, key recovery/rotation, production KMS, physical purge is added by M5. M6 separately adds bounded ciphertext-only export (see exports.md). All implementation validation uses synthetic data in isolated databases; existing services and meaningful data are untouched. M7 adds metadata-only access reporting separately; M8 local readiness and measurements are delivered; production privacy operations remain separate. Report identifiers and M6 export preserve the privacy limits above.
