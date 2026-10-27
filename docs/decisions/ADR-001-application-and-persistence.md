# ADR-001: One application with explicit JDBC transactions

Date: 2026-10-28. Status: M1 baseline; foundation authorized, later behavior planned.

Decision: Java 21, Spring Boot 4.1.0, Maven wrapper, one modular Spring MVC application, JDBC/Flyway, PostgreSQL reference storage and H2 disposable tests/demo. Explicit JDBC transactions expose chain-head locking. Pin unmanaged dependencies; retain Boot-managed JUnit.

Alternatives: microservices/broker add ordering/deployment complexity. JPA is viable but unnecessary for the small immutable schema and explicit SQL. H2 alone cannot establish reference-database correctness.

Consequences: simple setup and visible transactions; manual mapping/SQL, two-database validation and serialized global writes. No production-scale claim without evidence.
