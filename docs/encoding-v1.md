# Audit encoding and ingestion contract v1

Implemented in M2. This is a custom versioned encoding, not a claim of RFC 8785 conformance. Any incompatible change requires a new version and ADR.

## Input rules

POST /audit/events accepts one JSON object containing eventType, actorId, resourceType, resourceId, payload, and optional timestamp. Unknown envelope fields, duplicate object keys at any depth, trailing JSON values and nonstandard numbers are rejected. Required strings are nonblank, at most 255 UTF-16 code units; payload is an object with depth at most 20 from its root. Body limit is 262144 bytes, enforced using bounded reads even without Content-Length. The caller cannot set event ID, sequence, ingestion time or hashes. Validation messages do not echo supplied values.

Numbers are parsed as BigInteger/BigDecimal without binary-floating-point conversion. Parser numeric tokens are limited to 128 characters; normalized decimal precision and absolute scale are each at most 128. Non-finite/binary-floating-point values are unsupported. Unpaired Unicode surrogates are rejected.

Optional timestamp is parsed as an ISO-8601 instant, normalized to UTC, limited to years 0001-9999 and microsecond precision; higher precision is rejected rather than silently rounded. An absent timestamp uses server ingestion time, truncated to microseconds. An explicit null timestamp is invalid. Ingestion time is captured before locking; sequence, not timestamp, defines append order.

## Canonical representation

The envelope contains exactly actorId, eventId, eventType, ingestedAt, payload, previousHash, resourceId, resourceType, schemaVersion, sequence and timestamp. UUID uses lowercase standard text; schemaVersion=1; timestamps use UTC with exactly six fractional digits.

- Object keys sorted recursively by Java String natural order (UTF-16 code-unit order); no Unicode normalization.
- Array order preserved. Null/boolean use JSON literals.
- Numbers strip insignificant trailing zeros, normalize negative zero to 0, and use plain decimal notation without exponent.
- Strings quote `"` and `\` with backslash. U+0000-U+001F use lowercase four-digit `\u` escapes, including newlines/tabs. Other valid Unicode is retained literally, encoded as UTF-8. Slash is not escaped.
- No insignificant whitespace, BOM or trailing newline.

Hash input is ASCII `AUDIT-CHAIN-V1`, one NUL byte, a four-byte unsigned big-endian canonical-byte length, then exact canonical UTF-8 bytes. Store SHA-256 as 64 lowercase hexadecimal characters. The canonical envelope includes previousHash, so the one record_hash commits content and predecessor. Genesis predecessor is 64 zeros. Store the exact bytes; do not reserialize using a default JSON serializer when verifying.

Independent Python hashlib vectors:

| Canonical content | Expected record hash |
| --- | --- |
| `{}` (encoding-only fixture) | `41543cbd1c77d0846505e8c10b91960233f20f4906c0e37c9aac741c11ff0365` |
| Full fixed envelope in AppendContract.fixedEnvelopeMatchesIndependentGoldenHash | `f5859b69d733ec8302f8ee715377907cea15134865984d2f38ee59f29ad363bb` |

## Protected payload representation

AUDIT_PROTECTED_PATHS is a comma-separated list of RFC 6901 pointers into the payload (default /accountNumber,/personalIdentifier). Match exact object properties or array indices; use ~0/~1 for literal tilde/slash. Overlapping paths, invalid escapes, duplicates and paths over 1024 characters fail configuration. This comma-separated configuration cannot represent a property name containing a comma. Missing configured paths are optional and create no key. Protect an entire matching value, including nested content or null. Reject caller-supplied $auditProtected keys anywhere to avoid marker impersonation.

Each match gets a fresh 32-byte field key and 12-byte nonce. AES-256-GCM uses a 128-bit authentication tag. Plaintext is the canonical JSON value. Stored value becomes:

```json
{"$auditProtected":{"version":1,"algorithm":"AES-256-GCM","nonce":"<Base64>","ciphertext":"<Base64 ciphertext plus GCM tag>"}}
```

Ciphertext is generated once, retained byte-for-byte and committed by the chain. Identical plaintext across events intentionally yields different ciphertext/hashes.

Field keys are wrapped by another AES-256-GCM operation under a master key supplied outside PostgreSQL, with a fresh 12-byte wrap nonce. payload_key stores Base64(wrapNonce) + period + Base64(wrappedKeyWithTag), keyed by event ID and pointer, version=1. No plaintext field key or sensitive plaintext digest is stored in the database. AAD is UTF-8 `AUDIT-FIELD-V1`, NUL, kind (value or key), NUL, UUID, NUL, pointer. This prevents moving ciphertext/wrapped keys between events or fields without authentication failure. Temporary raw field-key buffers are cleared after cryptographic use; this is not a claim of complete JVM memory erasure.

AUDIT_MASTER_KEY_BASE64 must decode to 32 bytes for PostgreSQL. H2 permits an ephemeral master key in application memory because its store is disposable. Production startup must not enable that convenience for persistent data. Back up a real master key securely outside Git/database and preserve it across restarts. A database-only attacker sees wrapped keys but not the master key. Application-process compromise and combined key/database backup compromise remain risks; this is not a KMS.

M5 will remove individual wrapped-key rows and add audited redaction semantics. M2 does not expose redaction, key rotation or decryption HTTP operations. Backups/prior exports still constrain erasure claims.

## Transaction and API boundaries

Validate/protect input first; lock singleton chain_head with SELECT FOR UPDATE in one Spring transaction, allocate next sequence, insert event and wrapped keys, update head and commit. A failure anywhere rolls back event, keys and head, without leaving filesystem keys. Database uniqueness protects event identity/sequence. No request idempotency guarantee: retry after an uncertain committed response can append another event. No automatic retry hides an uncertain outcome.

Controller success returns 201 with Location and receipt (eventId, sequence, timestamp, ingestedAt, previousHash, recordHash). Validation returns safe problem details (400, or 413 for body size). No PUT/PATCH/DELETE handler exists. Production HTTP still denies business requests until JWT/permissions in M4; standalone controller tests demonstrate adapter behavior without adding a permissive application profile. The receipt Location becomes readable with the M3 query implementation, subject to M4 authorization.

Runtime database grants, query/full-chain verification, external checkpoints, redaction and export remain later milestones. The append path does not perform an O(n) integrity scan before each write.
