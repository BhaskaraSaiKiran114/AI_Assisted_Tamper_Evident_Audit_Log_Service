# Milestone 1 validation and delivery evidence

Date: 2026-10-28 (Asia/Calcutta). Status: M1 complete, 1/8 milestones (12.5% planning progress). Scope: engineering governance and runnable project foundation only. User authorization: "Implement milestone 1 now." Human acceptance of delivered artifacts is pending.

## Environment

- Initial active JDK: Temurin 17.0.17; global mvn not on PATH. Existing Maven 3.9.16 distribution used only to generate the official wrapper.
- Verification JDK: portable Eclipse Temurin 21.0.12.1+1, downloaded through Adoptium metadata and checked against its published SHA-256. Installed under ignored .tools/, not system-wide.
- Wrapper: Apache Maven Wrapper 3.3.4, only-script; Maven 3.9.16. Maven ZIP verified against Maven Central SHA-512; its SHA-256 is pinned in wrapper properties.
- Local Maven dependency cache and wrapper runtime under ignored .tools/. JDK/caches are not deliverables.
- Spring Boot pinned to 4.1.0; Springdoc pinned to 3.1.0. JUnit supplied by the Boot dependency BOM.

## Commands and results

| Check | Result |
| --- | --- |
| Read assignment | All four pages extracted locally during architecture analysis; source not copied into project |
| Initial Java/Maven/Docker inspection | Java 17 active; no global mvn; cached Maven available; Docker CLI installed but daemon unavailable |
| Official wrapper generation | Passed using Maven Wrapper Plugin 3.3.4 |
| JDK/Maven download checksums | Passed vendor SHA-256 / Maven Central SHA-512 respectively; wrapper SHA-256 pinned |
| Java 21 clean verify | Passed; exit code 0, BUILD SUCCESS, completed 01:16:49 Asia/Calcutta; 54.656 seconds on successful retry |
| Toolchain / compiler | JDK/Maven Enforcer rules passed; javac release 21 |
| Checkstyle | Passed, zero violations across main/test sources |
| Foundation JUnit tests | Passed: 3 run, 0 failures, 0 errors, 0 skipped; 5.657 seconds |
| Executable package | Produced target/audit-log-service-0.1.0-SNAPSHOT.jar (40,489,709 bytes); Boot repackage completed |
| Failsafe | Executed configured phases; no *IT reference-database tests exist yet, so no independent PostgreSQL success is claimed |
| Documentation links and patch hygiene | Passed local-link/trailing-whitespace check over 37 deliverable files and git diff --check; final record edits checked again before delivery |
| Secrets/ignore baseline | No private-key/AWS-key/JWT-pattern findings in deliverable review; local tools, IDE files, .env, secrets, exports and target ignored; .env.example remains deliverable. Limited check, not a comprehensive scanner |
| PostgreSQL execution | Not run: Docker daemon unavailable; profile/Compose configuration only |
| Dependency-security scan | Configured opt-in, not executed; normal verify is not this scan |
| Performance/scenario A/B/C tests | Not applicable to foundation; planned with behavior in M2-M8 |
| External CI | Workflow configured; not executed remotely |

The successful build invocation used:

```powershell
$env:JAVA_HOME = '<project>\.tools\jdk21\jdk-21.0.12.1+1'
$env:MAVEN_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
$env:MAVEN_USER_HOME = '<project>\.tools\maven-user-home'
.\mvnw.cmd '-Dmaven.repo.local=.tools/repository' --batch-mode --no-transfer-progress clean verify
```

These project-local paths describe this verification environment. Normal setup uses the engineer's JDK 21 and default Maven cache as documented in README.

## Failed attempts and repairs

1. Initial requirement extraction hit Windows console encoding for Unicode; reran Python in UTF-8 and read all four pages successfully.
2. Network calls under the restricted environment could not open sockets. Requested environment escalation and retried; no alternate tool was used to bypass it.
3. Portable JDK's default trust store did not recognize the network certificate chain for Maven Central. Retried using Windows-ROOT trust, preserving certificate verification. Never enabled insecure TLS.
4. A documentation patch attempted to delete/add README in one patch; the patch tool rejected it without applying the batch. Reissued using an in-place update and added the remaining files normally.
5. Initial dependency resolution waited in network reads; Springdoc's transitive parent declares extra Spring snapshot/milestone repositories. Interrupted that attempt and added reviewed Maven Central-only mirror settings with bounded connection/request timeouts. The retry resolved dependencies, compiled, ran tests and packaged successfully. No project code changes were needed to make the tests pass.
6. An initial process-diagnostic attachment was denied by the sandbox; retried with environment permission to confirm Maven was waiting on dependency network reads.

## Observed non-fatal warnings

Flyway reports H2 2.4.240 newer than its verified H2 version, although the migration and smoke tests passed. Retain Boot-managed dependencies for this baseline and validate reference behavior on PostgreSQL in later milestones. Mockito's test bootstrap reports dynamic agent self-attachment warnings on Java 21; tests passed, and no suppression or security bypass was added. Neither warning is hidden or treated as proof of broad compatibility.

## Review boundaries

No business ingestion/query/verification endpoint exists yet. JWT validation and Swagger endpoint behavior are deferred to M4; deny-all protects the foundation meanwhile. The chain-head migration is not a working event chain. H2 smoke results cannot establish PostgreSQL semantics. No attestation signed, Git commit fabricated, external publication performed, or private remote/reviewer access claimed.
