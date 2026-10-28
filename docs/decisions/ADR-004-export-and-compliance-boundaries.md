# ADR-004: Signed exports and bounded compliance reporting

Date: 2026-10-28. Status: export implemented M6; see ADR-008 and ../exports.md for frozen format/limits. M7 reporting is implemented; see ADR-009 and ../access-report.md for explicit vocabulary, exclusions and limits.

Decision: filtered export includes canonical selected events, headers bridging gaps, snapshot metadata and signed manifest. Independent verifier trusts the signing identity out of band. No plaintext sensitive/redacted values. Account-access reports use explicit action/outcome vocabulary and account/actor/time filters without payload by default.

Alternatives: selected hashes cannot bridge sparse gaps; an embedded digest is replaceable; an embedded public key does not establish signer identity. All unrelated payloads violate minimization. Actual regulatory obligations cannot be inferred without jurisdiction/source requirements.

Consequences: proof headers may scale with chain range and leak structure. Included-content integrity is distinct from filter completeness or upstream truth. Report assumptions and legal/operational exclusions precede implementation.
