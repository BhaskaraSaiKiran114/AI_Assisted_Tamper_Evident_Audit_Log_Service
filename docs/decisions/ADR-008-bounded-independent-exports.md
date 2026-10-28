# ADR-008: Bounded signed exports and a dependency-free verifier

Date: 2026-10-28. Status: implemented under M6 authorization; refines ADR-004.

Decision: authenticated GET /audit/exports selects by actor/resource with AND semantics and explicit archive opt-in. Export only original protected canonical bytes, no field keys or decrypted protected values. Carry minimal sequence/previous/current hash headers for the whole snapshot, and selected membership/status in a signed ZIP body. Keep the public trust anchor out of the bundle. Use a separate configured RSA signing key; unavailable key fails exports closed without breaking other APIs.

Choose a fixed ordered UTF-8 manifest and bounded binary body rather than a general JSON/ZIP dependency in the independent tool. Explicit field names/version/algorithm, exact-byte SHA-256 and JCA signatures keep the format reviewable. A Java 21 classifier JAR containing only ExportBundle and its record classes runs without Spring, Maven, database, network or third-party libraries. Full producing-side canonical validation and independent selected-byte rehashing have separate responsibilities; no generic offline JSON canonicalizer is claimed. Exact format and trust limits are in ../exports.md.

Use the established database-specific snapshot isolation, full chain/lifecycle validation, 60-second transaction timeout and a separate bounded export semaphore. Bound chain headers, selected count and uncompressed bytes; reject oversized output atomically, with no partial bundle or silent truncation. No migration or runtime privilege extension is needed.

Alternatives rejected: embedded self-trusted public keys; unsigned mutable digests; JWT key reuse or process-random signing fallback; exporting all unrelated payloads; shipping a Boot JAR that needs a service/database for verification; unbounded in-memory exports. A range/checkpoint or Merkle format could support larger chains, but changes the existing commitment/trust model and is outside M6.

Trade-offs: whole-chain header count scales with history, signed hidden-content links rely on the signer, filter completeness is not proved, metadata/unprotected fields remain visible, lifecycle status is a signed assertion rather than a complete independently replayable lifecycle database. In-process key custody, rotation/revocation, external checkpoints and performance need further operational design. Prior exports and retained key copies constrain erasure claims. These limits do not justify regulatory or production-readiness certification.
