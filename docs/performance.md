# M8 local performance baseline

Measured 2026-10-28T09:47:57.216865300Z (15:17:57 Asia/Calcutta) with PostgresReadinessExercise in the opt-in readiness profile. [Sanitized raw values](validation/milestone-8-performance.json). No latency/throughput acceptance target was specified, so these are observations, not an SLO pass or capacity guarantee.

Windows 11 x64, Intel Core i7-1260P, 16 logical processors, about 16.8 GB reported physical memory; JVM maximum heap about 4.2 GB. Java 21.0.12.1, Boot 4.1.0, Framework 7.0.10, Security 7.1.2, Tomcat 11.0.27, PostgreSQL JDBC 42.7.14 and PostgreSQL 16.3, actual audit_runtime pool. Client/server/database share one machine; client/server share the test JVM. Loopback HTTP/1.1 has no TLS; real JWT validation, encryption and SQL transactions are exercised. The final advisory import completed before this measurement. Background OS workload was not controlled/profiled.

Warm up 50 writes, then reset only the guarded synthetic store. Submit 1000 measured writes at concurrency four using one reusable HttpClient: 218-byte UTF-8 JSON, one protected field, recognized outcome and fixed occurrence time. Worker latency excludes time waiting in the executor queue; throughput measures the complete submitted batch. HTTP statuses/counts and final intact verification are asserted. audit_event allocation after these writes was 1,826,816 bytes, excluding other tables/WAL/cache/database overhead and backups.

| Operation | Samples / concurrency | p50 ms | p95 ms | p99 ms / maximum |
| --- | --- | --- | --- | --- |
| Append | 1000 / 4 | 20.815 | 26.570 | p99 36.099; max 44.617 |
| Query 50 restored events | 10 / 1 | 61.360 | 86.854 | max 86.854 |
| Full verification, 1000 records | 5 / 1 | 240.300 | 289.597 | max 289.597 |
| Metadata report, page 50 | 10 / 1 | 22.389 | 23.191 | max 23.191 |
| Protected signed export, 1000 records | 3 / 1 | 413.095 | 467.180 | max 467.180 |
| Archived page 50 / batch 100 | 3 / 1 | 1128.423 | 1162.281 | max 1162.281 |
| After lifecycle verify, 1102 records | 3 / 1 | 254.132 | 371.252 | max 371.252 |
| After lifecycle export, 1000 selected | 3 / 1 | 422.588 | 441.954 | max 441.954 |

Append batch elapsed 5.357 seconds, throughput 186.665/second. Percentiles use nearest rank; with three/five/ten repetitions p95 is the maximum. Read/export timings follow writes with warm caches/JIT and no concurrent writes. Export includes source/lifecycle checks, packaging, signature and producer-side verification.

The second phase inserts 100 aged synthetic fixtures through the runtime appender, erases one original field through HTTP and archives exactly 100 records in one default-sized batch. Single observed redaction took 22.508 ms; the 100-record archival command took 62.726 ms. Two administrative events bring the chain to 1102. Three archived-page requests return 50 of the aged records with explicit archive permission, repeating committed-proof validation; their median 1128.423 ms exposes the cost. Verification/export after lifecycle actions remain intact. No original event timestamp is rewritten and no system clock is changed; the lifecycle fixture clock controls age eligibility.

Reproduce with scripts/verify-postgres.ps1 -Readiness. No numeric latency assertion pins machine-dependent values. The two-minute append batch deadline and HTTP/database timeouts are operational bounds, not promised SLOs. Default batch 100/page 50 was measured; maximum batch 500/page 200 and repeated actions at scale were not.

Earlier runs measured 321.419/s before dependency patches and 182.493/s on the first patched baseline without the added fanout phase; their ignored raw outputs remain .tools/m8-readiness and .tools/m8-readiness-patched. Final outputs are .tools/m8-readiness-final. Uncontrolled host/JIT/cache/workload differences prevent causal patch-performance comparison. Do not combine these runs into a statistical performance guarantee.

Limits: one host/run, small synthetic payload, 1000-event base/1102-event lifecycle dataset, no TLS/WAN, soak, GC/lock profiling, mixed workload, production key service or 10000-header export-cap measurement. Query restoration/proof validation use per-event lookups, including repeated batch-proof checks; verification/export remain O(n), appends lock a global head and exports buffer multiple bounded copies. Production work should measure agreed workloads/budgets and consider transaction-local proof reuse/index tuning without weakening integrity. No legal/production capacity claim follows from this baseline.
