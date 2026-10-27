# ADR-002: Versioned SHA-256 chain and transactional head

Date: 2026-10-28. Status: design baseline; exact encoding and implementation M2/M3.

Decision: canonical bytes commit identity, sequence, event fields, timestamps, protected payload and predecessor. SHA-256 with domain/version framing, zero genesis, singleton lock and atomic event/head persistence. Preserve bytes; verify searchable columns against them using a consistent snapshot.

Alternatives: default serializer hashes are unstable; payload-only hashes omit metadata; auto-increment IDs do not protect order; process-local locks fail across instances; blockchain infrastructure exceeds scope.

Consequences: deterministic integrity checks, O(n) full traversal and one write bottleneck. Coherent rewrite/rollback can evade database-only checks; independently retained signed checkpoints are needed. Changing a stored record does not physically rewrite downstream hashes; downstream commitments become invalid relative to the changed chain.
