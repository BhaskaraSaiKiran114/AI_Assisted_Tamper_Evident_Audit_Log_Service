# Signed export contract - M6

GET /audit/exports requires AUDIT_EXPORT and at least actorId or resourceId. Optional resourceType requires resourceId; supplied filters combine with exact AND matching. includeArchived defaults to false. Setting it to true additionally requires AUDIT_LIFECYCLE. AUDIT_EXPORT does not grant ordinary decrypted reads, verification or documentation access. Unknown/repeated parameters, blank/overlong identifiers (>255 UTF-16 units) and nonliteral true/false values are 400. No cursor/time/eventType parameters are accepted by this bounded export operation.

Success is a complete application/zip attachment named audit-export.zip with no-store security headers. Export has no database write or implicit lifecycle action. Default archive selection excludes archived business content; all chain headers remain in proof. Archived selection requires explicit opt-in even for an authorized operator. Selected archived/redacted status is validated in the same snapshot before signing. Administrative payload is included only if it matches the explicit filter.

## Local setup and independent use

Generate a **separate** export key pair once. The existing Java source helper can create an RSA key pair in another ignored directory, with owner-restricted private-file permissions and overwrite refusal. Do not reuse the JWT signing key, payload master key or cursor key. The public key must reach the recipient through a trusted channel independent of the bundle.

```powershell
& "$env:JAVA_HOME\bin\java.exe" scripts/LocalJwt.java init .tools/export-signing
$env:AUDIT_EXPORT_PRIVATE_KEY_BASE64 = (Get-Content .tools/export-signing/private-key-base64.txt -Raw).Trim()
# Start/restart the service with this environment plus its usual JWT/database settings.
$exportToken = & "$env:JAVA_HOME\bin\java.exe" scripts/LocalJwt.java token .tools/jwt 'AUDIT_EXPORT'
$exportHeaders = @{ Authorization = "Bearer $exportToken" }
Invoke-WebRequest 'http://127.0.0.1:8080/audit/exports?actorId=synthetic-actor' -Headers $exportHeaders -OutFile .tools/audit-export.zip
& "$env:JAVA_HOME\bin\java.exe" -jar target/audit-log-service-0.1.0-SNAPSHOT-verifier.jar .tools/audit-export.zip .tools/export-signing/public-key-base64.txt
if ($LASTEXITCODE -ne 0) { throw 'Export verification failed' }
```

Maven package produces both the Boot application and the small classifier verifier JAR. Copy the verifier, bundle and trusted public-key file to another machine with Java 21; no running service, database, Maven, Spring, Jackson or network is needed. CLI success prints VERIFIED, selected count, head sequence/hash and export time, exiting 0. Invalid/truncated/unsupported input, wrong key or failed proof exits 1 with a generic INVALID message. Never treat a downloaded public key in the same untrusted delivery as proof of signer identity. Validate the verifier software through your normal trusted distribution/review process too.

AUDIT_EXPORT_PRIVATE_KEY_BASE64 is a Base64 PKCS8 RSA CRT private key, 2048-8192 bits. The public SPKI key is derived locally. Blank configuration keeps other APIs operational but authenticated exports return 503; malformed/weak configuration fails startup. There is no temporary-key fallback. Keep the private key in a reviewed secret store/environment outside Git and logs. In-process signing is a prototype compromise, not production KMS/HSM isolation. Key replacement requires restart; retain old trusted public keys for old exports. Key revocation/discovery/automatic rollover are not implemented. Public-key SHA-256 fingerprint is included in the signed manifest; it is an identifier, not a trust bootstrap.

## Resource and snapshot boundary

One separate read-only transaction uses PostgreSQL REPEATABLE_READ or H2 SERIALIZABLE. It reads the head, traverses every event from genesis, validates canonical content/query columns, continuity and lifecycle evidence, selects protected records and signs the complete result. Concurrent append/erasure can commit while the earlier snapshot remains valid. A later operation does not retract a bundle already emitted. Corrupted unselected material also prevents export; no partial valid-looking bundle is returned.

Defaults: AUDIT_EXPORT_MAX_CONCURRENT=2 (1-4), MAX_RECORDS=10000 (1-10000), MAX_SELECTED=1000 (1-1000), MAX_BYTES=16777216 (1024-16777216), all prefixed AUDIT_EXPORT_. MAX_RECORDS bounds the whole chain, not just the selected range. MAX_BYTES conservatively bounds the uncompressed records body; selected canonical bytes, framing and worst-case path encoding count toward it. Limits may be lowered, never raised above portable format bounds. Exceeding limits returns 413; narrow the selection where useful, but a chain above the proof-header cap requires a future reviewed range/checkpoint format. No truncation, background job or export pagination is claimed. Capacity exhaustion is 429; the permit is released on success/failure. The database transaction timeout is 60 seconds, not a hard CPU or network SLA. Construction buffers bounded data in memory and temporarily holds multiple copies; two concurrent maximum exports can use substantially more than 32 MiB. Load measurement remains M8.

## Portable format version 1

ZIP contains exactly manifest.txt, records.bin and signature.bin, no extracted files or embedded public/private/field keys. Duplicate/unknown/directory entries are rejected by the verifier. Limits apply to expanded entries (body 16 MiB, manifest 64 KiB, signature 1024 bytes), with compressed input bounded to 17 MiB. ZipInputStream validates entry data/CRC; ZIP transport metadata or irrelevant trailing container bytes are not authenticated as content. The signed manifest and body are authoritative.

manifest.txt is UTF-8, LF terminated, with exact ordered key=value lines (no comments/extra fields): format, canonicalization, signatureAlgorithm, createdAt, actorId, resourceId, resourceType, includeArchived, headSequence, headHash, selectedIds, bodySha256, keyFingerprint. Values are AUDIT-EXPORT-V1, AUDIT-CHAIN-V1, SHA256withRSA, ISO instant, then filters encoded as unpadded Base64URL UTF-8 or '-' for absent, literal Boolean, decimal sequence, lowercase hash, comma-separated selected UUIDs in chain order, SHA-256 of exact records.bin and SHA-256 of trusted SPKI key bytes. signature.bin is RSASSA-PKCS1-v1_5/SHA-256 over **exact manifest bytes**; there is no attacker-selectable algorithm fallback. Signature is checked against the recipient's pinned key before parsing proof data. Manifest reconstruction enforces byte-for-byte field order/encoding.

records.bin uses Java DataOutputStream big-endian framing:

1. int version=1; int headerCount, equal to headSequence.
2. For every sequence 1..head: long sequence; 32 raw previous-hash bytes; 32 raw record-hash bytes; byte selected (0/1).
3. Only when selected: modified-UTF UUID (DataOutputStream.writeUTF); byte archived (0/1); int redacted-path count; each modified-UTF path; int canonical-byte length; exact stored canonical bytes. Path count is bounded to 1000 per selected record, path length to 1024 UTF-16 units. The exporter fails rather than emits an unsupported bundle.

No unrelated event identity, actor/resource/type, timestamps or payload is carried by a bridging header; sequence/hashes and chain length still disclose structure. Selected records retain their original protected payload envelopes. Field keys, decrypted sensitive values and wrapped keys are never exported. Unprotected payload fields and selected metadata remain visible and must be handled accordingly. Do not interpret ciphertext-only export as plaintext anonymization.

The verifier checks protocol versions, pinned RSA key/fingerprint/signature, body digest, bounded framing/counts, genesis and predecessor continuity, final head, selected ID membership/order/uniqueness, archive mode, selected exact-byte content hashes using AUDIT-CHAIN-V1 plus NUL plus 4-byte length, and top-level envelope identity/sequence/schema/predecessor/filter fields. Its small top-level scalar reader skips nested payload objects; it is not a general JSON normalization library. The producing service separately enforces the full frozen canonical envelope contract before signing. Independent hash verification does not require decryption keys; missing keys without audited erasure do not become claimed redactions.

## Claims and limits

The signed manifest authenticates the snapshot, selection, bytes, headers and status assertions under the trusted signing identity. For selected content, its exact hash is recomputed. For unrelated headers, the verifier checks signer-attested links; their hidden content cannot be rehashed without disclosing that content. This is the approved sparse signed-evidence design, not a Merkle inclusion proof or proof of filter completeness. Archived/redacted flags/paths are signed snapshot assertions after server validation; the bundle does not include every lifecycle SQL row or independently establish physical key destruction. Full online lifecycle checking remains separate.

The bundle cannot prove omitted matching records, unreported upstream events, legal compliance, erasure of previous copies or historical truth after signing-key compromise. A coherent privileged database rewrite before export can be signed as a new history; independent externally retained checkpoints are not operated in this milestone. Prior exports retain original protected ciphertext; an old wrapped key plus master key may recover it. Backups, WAL, replicas, earlier MVCC snapshots/in-flight reads, caches and previously disclosed values remain privacy limits. M7 metadata-only account-access reporting is a separate implemented capability (see access-report.md); M8 local readiness and measurements are delivered; human acceptance/production operation remain separate.
