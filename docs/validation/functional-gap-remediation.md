# Approved gap remediation validation

2026-10-28, Asia/Calcutta. User approved implementing the four priority fixes after a read-only gap review. The eight original milestones remain complete as historical engineering deliveries; this is a scoped follow-up, not a new accuracy score or personal affirmation.

## Changes and acceptance

- Resource authorization: exact issuer-signed resourceType/resourceId assignments or explicit global operator grant, independent action permissions, SQL-scoped query/report candidates, no out-of-scope export content, authorized ingestion, targeted redaction before replay, global-only verification/retention, principal/assignment-bound cursors. No event/hash/migration/bundle-format rewrite. See ADR-010.
- Measured coverage: JaCoCo combined unit/IT reports; bundle line/branch/method 85%/75%/85%, security package 90%/80%/90%, no application exclusions. Required risk/route cases are mapped in traceability, rather than described as endpoint coverage percentages.
- CI evidence: PostgreSQL/readiness required suites, rejected failures/errors/skips/missing coverage, raw reports and SHA/run/attempt artifact naming, separate fail-closed dependency audit. Build manifest validates the tested source copy before and after execution; uncommitted code is identified with exact input hashes and a dirty flag.
- Governance: current identity/date retained, repository/branch/base commit/scope recorded, actual tested input/report identities generated, personal affirmation/final reviewed commit/actual remote CI run still human-owned. AI generation/rejections/limitations recorded.

## Execution environment and failures

Windows 11, portable Java 21.0.12.1+1, Maven wrapper 3.9.16, actual PostgreSQL 16.3 in a fresh disposable cluster, restricted audit_runtime credentials plus separate migration/admin fixtures; H2 demonstration contracts. CI PostgreSQL 17.6 is configured, not independently executed remotely here.

Initial JaCoCo download was blocked by sandbox networking and then the portable JDK certificate store. Approved network execution with the existing documented Windows-ROOT trust store completed the download; TLS validation was not disabled. The Windows Maven wrapper needed a null-safe directory-target check for ordinary non-junction Maven-user-home directories.

The initial root clean hit the user's running application's JAR lock. Validation therefore uses a non-ignored-source copy under .tools/remediation-work and the existing absolute JDK/Maven cache, without stopping/replacing the running 8080/8081 demo or modifying its data. A first full application-test run caught a standalone adapter fixture with no identity; it was changed to supply an explicit authenticated operator, without production bypasses. Subsequent Surefire and JaCoCo gates passed, but sandbox temporary-directory cleanup denied the offline verifier test's JUnit extension; that run is a failure, not an accepted full pass. Full reference/readiness validation was rerun with required environment permission. Its new restricted-pool resource contracts passed, but the combined suite exhausted PostgreSQL connection slots because cached contexts each retained their default ten-connection pools. Test fixture pools were bounded to maximum four/minimum one; no production pool defaults were changed. The failed run was retained and the complete clean reference build rerun.

## Final executed result

Full clean reference/readiness build passed at 23:01:31 +05:30 on 2026-10-28. **238 Java checks passed: 151 Surefire + 87 Failsafe (83 PostgreSQL, one packaged CLI, three readiness)**; zero failures/errors/skips and zero Checkstyle violations. The fresh disposable PostgreSQL cluster stopped cleanly. Six separate Python evidence-gate regressions passed; Python syntax checks passed. Workflow YAML parsed successfully with the cached SnakeYAML library (syntax/job presence only, not an executed GitHub run). The actual Java local-token helper passed default scoped, explicit --global and invalid-mode checks without printing credentials. Final source inputs and the copied manifest digest were rechecked against the tested manifest; deliverable hygiene reported zero findings. No fresh advisory scan or remote CI execution is claimed.

| Counter | Bundle measured | Enforced minimum | Security package measured | Security minimum |
| --- | --- | --- | --- | --- |
| Line | 94.46% | 85% | 99.33% | 90% |
| Branch | 79.92% | 75% | 93.52% | 80% |
| Method | 96.35% | 85% | 100.00% | 90% |

The exact [tested input manifest](functional-gap-remediation-inputs.json) has SHA-256 `e9129126ab4773b0007770fa02752a5319e0d264883b8d0c606a08e78983ebf5`. The [validation record](functional-gap-remediation-results.json) contains actual suite totals, coverage counters, repository/branch/base commit, dirty=true, null remote CI URL, report digests and the generated timestamp. Raw local reports and HTML coverage are retained in `.tools/remediation-work/target`; raw build log is `.tools/remediation-reference-verify.log`. The copied validation record SHA-256 is `58c59ce1fad2bac76b8113419c46b3538037113644f5a8964f4233e9698610b1`.

These hashes identify the tested uncommitted build inputs, not the base commit alone. Documentation updated after execution is outside the declared source/build-input manifest. Final committed-revision validation and human review remain submission prerequisites.

## Evidence reproduction and ownership

From a clean source checkout: run python -m unittest discover -s scripts -p 'test_*.py'; Maven clean; python scripts/build-evidence.py --begin; Maven -Ppostgres-it,readiness verify with an explicitly consented disposable PostgreSQL fixture; python scripts/build-evidence.py --reference. The Windows runner accepts -EvidencePython and -EvidenceSourceRoot, creates/stops its own cluster and runs clean before manifest capture. Never capture a begin manifest after the test run to pretend it predates validation.

Raw reports and exact build inputs are generated under target/evidence, target/surefire-reports, target/failsafe-reports, target/site/jacoco and target/readiness. Local outputs live in the isolated build; CI uploads equivalent paths for 30 days. The final submission must retain these artifacts and an actual successful CI URL for its final reviewed committed revision. This working tree has no remediation commit or remote run, and none is fabricated. No archive has been generated; repository submission rather than a ZIP remains the assignment deliverable.

Unchanged limitations: 10,000 global-chain-header export ceiling; coherent privileged rewrite needs independent external anchoring; active-store crypto-erasure does not erase WAL/backups/replicas/prior copies; no production KMS, separate tenant chains/DB isolation or online JWT revocation. Shared snapshot heads/sequences/bridging hashes disclose global activity. These are documented boundaries and separate architecture proposals.
