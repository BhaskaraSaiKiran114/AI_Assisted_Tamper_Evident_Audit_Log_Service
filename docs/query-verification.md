# Query and chain verification contract

Implemented in M3 and enabled by M4 JWT permissions: AUDIT_READ for query/lookup, AUDIT_VERIFY for verification. Tests cover both standalone adapters and the actual security filter/decoder; no permissive runtime profile or update/delete operation exists. See security-and-api.md.

## Event queries

GET /audit/events accepts actorId, resourceType, resourceId, eventType, from, to, limit, cursor and includeArchived. Any filter combination is an AND; resourceId may be supplied without resourceType. Values use parameterized SQL with exact string matching. Time filters refer to caller occurrence time, not ingestion time, and use `[from,to)`. Omitted bounds are open. Explicit bounds must be supported ISO-8601 instants with microsecond precision, from < to when both are present. Unknown/repeated parameters, blank/overlong identifiers and invalid times are 400 errors.

limit defaults to 50 and must be 1-200. Results are in ascending immutable sequence order, using keyset pagination rather than offsets. Response shape:

```json
{
  "events": [],
  "limit": 50,
  "nextCursor": null,
  "snapshotHead": {"sequence": 0, "hash": "0000000000000000000000000000000000000000000000000000000000000000"}
}
```

Each event contains eventId, sequence, eventType, actorId, resourceType, resourceId, payload, timestamp, ingestedAt, previousHash, recordHash, archived and redactedPaths. GET /audit/events/{eventId} returns one event, 404 if absent, 400 for a malformed UUID. Committed bytes are checked against their digest and query columns before an event is returned. Protected values are restored using wrapped keys; raw keys are never returned. recordHash commits the stored protected representation, not decrypted response; use verification now or M6's signed bundle for evidence. Missing/malformed keys or authenticated-decryption failure is 503 unless matching validated lifecycle evidence proves erasure. M5 returns $auditRedacted markers and redactedPaths after erasure. Default reads hide archives; includeArchived=true requires both READ and LIFECYCLE. See lifecycle.md. AUDIT_READ is now enforced; no anonymous decryption endpoint exists.

## Cursor semantics

The first page captures head sequence/hash. Every page is restricted to event_sequence <= that upper sequence, so later appends cannot enter the traversal. Fetch limit+1 rows to determine whether another page exists; advance after the last returned sequence, including when matching rows have gaps due to filtering. Empty/exhausted pages return nextCursor=null. No exact count query or offset scan is required.

Cursor format: unpadded Base64URL(canonical JSON) + period + Base64URL(HMAC-SHA256 signature). JSON fields are version=2, after, upper, headHash and filterHash. The signature covers the exact JSON bytes. filterHash is SHA-256 over ASCII AUDIT-QUERY-FILTER-V1, a NUL, then canonical filter fields including limit, archive mode and normalized time bounds. All filters and the limit must remain identical on continuation; equivalent UTC offsets normalize to the same Instant. Request cursor size is bounded to 2048 characters; signatures are compared in constant time. Invalid signatures/format/version/bounds or changed filters produce 400.

The server compares the saved upper-sequence record hash with the saved head hash before continuing. Missing/changed anchor returns 409. The cursor is not a credential, encryption or independent integrity proof. Authentication is still required at every request once M4 enables business access. No cursor TTL is claimed. A default process-random signing key invalidates cursors on restart or across instances; configure a separate AUDIT_CURSOR_KEY_BASE64 (32 random bytes, Base64) for persistent/shared cursors. Keep it outside Git and the database; do not reuse the payload master key. Rotation invalidates existing cursors.

Each HTTP request gets a new database snapshot. The signed upper bound provides stable membership under normal append-only activity, not a historical MVCC snapshot held across requests. Direct datastore changes to earlier/unselected records can affect results without changing the anchor. Full-chain verification is the separate integrity check; a filtered page does not prove filter completeness or historical truth.

## Full-chain verification

GET /audit/verify accepts no filters or pagination. It traverses every stored event in sequence order, independent of query filters and key availability. M5 archival leaves all chain material in this traversal. Administrative events are also cross-checked against lifecycle actions, markers, target eligibility and erased-key absence; invalid proof reports LIFECYCLE_MISMATCH.

Result:

```json
{
  "intact": false,
  "checkedCount": 2,
  "snapshotHead": {"sequence": 3, "hash": "<snapshot head hash>"},
  "inconsistency": {"sequence": 2, "eventId": null, "type": "SEQUENCE_GAP"}
}
```

An intact result has inconsistency=null. checkedCount counts rows examined, including an offending row; for a missing sequence it may include the later row that exposed the gap. A missing record has no surviving eventId, so return null rather than inventing one. A missing/invalid head reports HEAD_MISMATCH at sequence 0 with zero examined rows; snapshotHead is null if no head exists.

Detection proceeds in sequence order, stopping on the first observed inconsistency:

1. Validate head shape/genesis; missing/invalid head is HEAD_MISMATCH.
2. Require each row's expected next sequence; a discontinuity is SEQUENCE_GAP at the first missing expected sequence. A row beyond an underreported head is HEAD_MISMATCH.
3. Require supported schema version and recompute SHA-256 over exact stored canonical bytes: UNSUPPORTED_SCHEMA_VERSION or CONTENT_HASH_MISMATCH.
4. Decode strict JSON, require canonical representation/object payload, and compare all indexed/duplicated fields (including identity, times, payload and previous hash) with the committed envelope: MALFORMED_CONTENT or INDEX_FIELD_MISMATCH. Equivalent JSON formatting of the duplicate payload column is semantically equivalent; exact committed bytes still remain authoritative.
5. Require predecessor continuity/genesis: PREVIOUS_HASH_MISMATCH. Recomputing one altered event's digest does not repair its successor's link.
6. Compare traversal length and final digest with snapshot head. Missing tail is SEQUENCE_GAP; wrong final digest is HEAD_MISMATCH.

This reports the first observable inconsistency, not proof of the original attacker action. Intact/broken results both return HTTP 200. Storage/transaction failures return safe HTTP 503 and never an integrity-success response. Unknown non-storage runtime failures remain 5xx, never intact=true. Existing exception policy keeps stack traces/internal details out of HTTP responses.

## Consistency and resource boundaries

ReadSnapshot starts a separate read-only transaction: PostgreSQL REPEATABLE_READ, H2 SERIALIZABLE. Never silently join a caller's weaker transaction. This also means uncommitted writes in an outer caller transaction are not part of these read results. Append transaction isolation is unchanged. The snapshot begins with the head read; concurrent appends can commit without causing a false break in that traversal.

The H2 concurrency test initially exposed phantom rows under REPEATABLE_READ. H2 SERIALIZABLE prevents that read anomaly; this is not a claim of full H2 write-serializability. See [H2 transaction isolation](https://h2database.com/html/advanced.html#transaction_isolation) and [PostgreSQL transaction isolation](https://www.postgresql.org/docs/16/transaction-iso.html).

Verification uses a closable JDBC stream and fetch size 100; PostgreSQL reads inside a transaction. H2 is a demo datastore and may materialize results internally. Application processing keeps one current event rather than accumulating the chain. Memory still depends on stored record size, and full verification is O(n). Decoding keeps strict nesting/string/number constraints without imposing the original request-byte limit on ciphertext-expanded stored records. Query decryption currently makes a bounded per-event key lookup (N+1 queries, at most 200 returned events).

Query transaction timeout setting is 30 seconds; verification is 60 seconds. These govern database behavior, not a hard CPU/wall-clock SLA. M4 limits simultaneous verification scans per process (default 2; excess is 429); distributed quotas remain future work; M8 records a limited local baseline without a production SLO. Independent checkpoints and whole-database rollback defense remain planned. M6 adds bounded signed exports and a dependency-free verifier; see exports.md. M5 implements archive/redaction; each page uses current lifecycle state in its own snapshot, not historical state across requests. Old v1 cursors are rejected. Lifecycle proof validation adds repeated target lookups; M8 measured nominal batch 100/page 50 proof cost (performance.md); larger fanout remains unmeasured. A privileged attacker coherently rewriting all rows/hashes/head can evade same-database verification; runtime credentials now lack event mutation/DDL privileges.
