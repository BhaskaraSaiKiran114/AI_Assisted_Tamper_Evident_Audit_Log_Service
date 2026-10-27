# ADR-003: Preserve chain material and redact through key erasure

Date: 2026-10-28. Status: protected envelope and wrapped-key persistence implemented M2; archival/redaction lifecycle remains M5. Master key stays outside PostgreSQL; H2 uses a disposable in-memory key. See `../encoding-v1.md`.

Decision: archive through separate policy/audit metadata, retaining immutable records. Encrypt designated sensitive payload values before hashing using per-field keys/authenticated encryption. Hash ciphertext representation; remove the field key to redact and append a lifecycle event without changing chain bytes.

This refines the assignment's original-value hashing challenge: the committed payload representation protects encrypted content instead of plaintext that later needs removal. Document and defend this interpretation. Plaintext hashes of low-entropy identifiers enable guessing.

Alternatives: row deletion or overwriting plaintext breaks the chain; retaining sensitive plaintext hashes risks inference; commitment-opening schemes need careful secret management; chameleon hashes introduce privileged rewrite trust.

Consequences: redactability chosen at ingestion, metadata outside configured fields retained. Verification proves ciphertext integrity, not erased-plaintext correctness/recoverability. Backups/caches/replicas/prior exports limit erasure. Local key storage is not enterprise KMS or legal-compliance proof.
