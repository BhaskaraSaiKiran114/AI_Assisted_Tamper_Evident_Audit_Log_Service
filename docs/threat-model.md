# Threat model and risk register

Protect event integrity/order, sensitive values/keys, administrative evidence, signing identity and restricted reports. Boundaries: producer to REST/JWT; API to JDBC; migrator vs application role; database vs field keys; database vs independent checkpoint; recipient vs signing trust; engineer vs AI/external services.

Assume malicious requests, compromised producers, accidental/operator edits and privileged datastore attacks. Producer-reported actors/events are not independently proven. Do not claim protection after simultaneous compromise of event, key and checkpoint stores.

| Risk | Planned mitigation and validation | Residual limit / milestone |
| --- | --- | --- |
| Event edit/insertion/deletion/reorder | M3 implements canonical/field/sequence/link/head checks; direct DB tests passed on H2/PostgreSQL | Coherent rewrite still needs independent checkpoint |
| Tail removal/whole DB rollback | Head comparison and signed external checkpoint | Same-store head can be rewritten; external operations incomplete in M1; M6/M8 |
| Concurrent append forks/lost head | Row lock, atomic transaction, unique sequence, rollback tests | Global write bottleneck; M2 |
| JSON/hash ambiguity | Versioned encoding/rejection rules/golden vectors/exact bytes | Independent verifier needs same encoding version; M2/M6 |
| Read head/rows from different database states | M3 ReadSnapshot and latch-controlled concurrent append test; H2 SERIALIZABLE / PostgreSQL REPEATABLE_READ | Per-request snapshots do not freeze history across pagination requests |
| Forged/drifting pagination | M3 HMAC-signed filters/limit/upper-head context, keyset ordering and anchor check | Default ephemeral key invalidates after restart; filtered pages do not prove completeness |
| Unauthorized read/lifecycle action | M4 implements pinned RS256, issuer/audience/time/subject validation and explicit permission matrix; actual runtime-role tests pass | Trusted issuer/process compromise remains; lifecycle routes are still denied |
| Sensitive data exposed by hashes | Hash encrypted field representation, no plaintext digest, separate keys | Metadata/unencrypted fields remain; M2/M5 |
| Redaction defeated by key copies | Key lifecycle, avoid logging, purge applicable copies | Backups/caches/prior exports limit erasure; M5 |
| Archive creates false chain break | Keep immutable content, separate audited lifecycle metadata | Archive-status tampering needs control; M5 |
| Export forgery/alteration | Signed manifest, bridging proof, independent verification and trust key | Sparse proof does not establish filter completeness; M6 |
| Injection/oversize/resource exhaustion | Parameterized queries, size/depth/page bounds, authorization/concurrency limits | Full scans remain O(n); M2-M4/M6/M8 |
| Secrets/payload leaked to AI/logs | Synthetic fixtures, no body/token logging, ignore rules/review | Human ownership of prompts/exports; all milestones |
| Supply-chain issue | Fixed versions, wrapper checksum, verified TLS and advisory scan | Scan data availability/upstream risks; M1/M8 |
| Startup schema-owner credentials or broad runtime grants | M4 separates Flyway/runtime pools, PostgreSQL V3 limits event/key/head privileges, real denial tests | In-process migration secrets remain until operations isolate migration jobs; H2 is demo-only |
| Authenticated full-scan exhaustion | M4 nonblocking per-process semaphore, 429 on capacity and release after failures | Distributed quotas and measured performance remain M8; no CPU/wall-clock SLA |

Failures must not report success: append rollback cannot advance head; verification timeout/error cannot return intact=true; missing field keys cannot expose ciphertext as plaintext; invalid bundle signature/proof/version must fail verification; failed lifecycle operations cannot claim completed redaction/archival.
