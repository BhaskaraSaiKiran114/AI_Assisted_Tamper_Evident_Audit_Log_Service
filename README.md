# Tamper-Evident Audit Log Service

Engineer-led Java prototype. Current scope: **milestone 1, engineering foundation**. Business audit, lifecycle, export and compliance APIs are planned and not implemented yet.

## Engineering baseline

- [Requirements and Scenario C clarification](docs/requirements.md)
- [Architecture/API blueprint](docs/architecture.md) and [decisions](docs/decisions/)
- [Eight milestones](docs/milestones.md) and [traceability](docs/traceability.md)
- [Governance](docs/engineering-governance.md), [threat model](docs/threat-model.md), [testing strategy](docs/testing-strategy.md)
- [M1 validation](docs/validation/milestone-1.md), [AI usage](AI_USAGE_LOG.md), [unsigned attestation](ATTESTATION.md), [living summary](docs/final-engineering-summary.md)

## Prerequisites

JDK **21**, with JAVA_HOME pointing to its directory. The wrapper downloads pinned Maven 3.9.16/dependencies on first use; no global Maven needed. Optional Docker Compose requires a running daemon for PostgreSQL 17.6.

Stack: Spring Boot 4.1.0, Spring MVC/JDBC/Security Resource Server, Flyway, PostgreSQL/H2, Springdoc OpenAPI/Swagger and Boot-managed JUnit. JWT and OpenAPI dependencies are present; behavior is deferred to M4. Public references: [Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html), [Springdoc](https://springdoc.org/), [Maven wrapper](https://maven.apache.org/tools/wrapper/).

## Build and run (disposable H2)

PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
.\mvnw.cmd spring-boot:run
```

POSIX:

```sh
export JAVA_HOME=/path/to/jdk-21
bash ./mvnw --batch-mode --no-transfer-progress clean verify
bash ./mvnw spring-boot:run
```

Or run with Java 21: `java -jar target/audit-log-service-0.1.0-SNAPSHOT.jar`.

Default `h2` profile keeps data in memory until process termination. PostgreSQL compatibility mode is not PostgreSQL proof. Flyway initializes one sequence-zero/genesis chain head; event storage arrives in M2. Server binds to 127.0.0.1:8080. GET /actuator/health is public/minimal; other requests denied. OpenAPI/Swagger is disabled until M4. No token issuer is implemented.

## PostgreSQL local profile

```powershell
Copy-Item .env.example .env
# Edit .env and replace the sample password before starting.
docker compose up -d postgres
$env:SPRING_PROFILES_ACTIVE = 'postgres'
$env:AUDIT_DB_URL = 'jdbc:postgresql://localhost:5432/auditlog'
$env:AUDIT_DB_USERNAME = 'auditlog'
$env:AUDIT_DB_PASSWORD = '<same password you put in .env>'
.\mvnw.cmd spring-boot:run
```

Compose reads .env; Spring Boot does **not** automatically read it. Set shell variables as shown. If POSTGRES_PORT changes, update AUDIT_DB_URL. Never commit credentials. `docker compose stop` preserves data. The development account owns this local database for Flyway; deployment needs separate migrator and restricted runtime accounts. PostgreSQL has no default password in app configuration. Do not activate H2 and PostgreSQL profiles together.

## Quality gates

Normal `clean verify` enforces toolchain, checks Java style, compiles, runs foundation JUnit tests and packages the app. Failsafe is ready for future integration tests. The CI workflow uses Java 21; adding it is not an external CI execution.

```powershell
.\mvnw.cmd -Psecurity-audit verify
git diff --check
```

Opt-in security-audit uses current vulnerability data and fails on CVSS >=7. NVD_API_KEY can improve retrieval; never commit it. Normal verify is not a vulnerability scan. PostgreSQL/scenario/performance validation is tracked separately.

If enterprise TLS inspection requires Windows certificate trust, configure your organizational CA or, when appropriate for the shell:

```powershell
$env:MAVEN_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
```

Do not disable TLS verification. Project Maven settings mirror external repositories to Maven Central, avoiding extra snapshot/milestone repositories declared by transitive POMs. Bounded connection/request timeouts prevent indefinite downloads. If your organization requires a repository manager, supply your reviewed alternative with `-s path/to/settings.xml`; never put credentials in the committed settings file. Local tools/build/data/secrets/exports/IDE files are ignored.

## Ownership and submission

The candidate reviews outputs, completes their own attestation, and maintains genuine history. The assignment expects a private repository/reviewer access at submission. No publication/sharing occurs in M1. Do not commit the confidential assignment or real client data. Approval/evidence rules are recorded in governance and AI usage.
