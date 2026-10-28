package com.example.auditlog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;

import org.springframework.test.context.DynamicPropertyRegistry;

final class PostgresFixture {

    private static final String MIGRATION_PASSWORD = random();
    private static final String RUNTIME_PASSWORD = random();
    private static final String MASTER_KEY = random();
    private static boolean bootstrapped;

    private PostgresFixture() { }

    static void requireConsent() {
        String url = System.getenv("AUDIT_TEST_DB_URL");
        if (!"yes".equals(System.getenv("AUDIT_TEST_DB_DESTRUCTIVE_OK"))
                || !"yes".equals(System.getenv("AUDIT_TEST_CLUSTER_ROLES_OK")) || url == null
                || !url.matches("jdbc:postgresql://[^/]+/audit_m2_test(?:\\?.*)?")) {
            throw new IllegalStateException("Tests require a fresh disposable cluster, audit_m2_test database, and both consent flags");
        }
    }

    static Connection admin() throws SQLException {
        requireConsent();
        return DriverManager.getConnection(System.getenv("AUDIT_TEST_DB_URL"),
                System.getenv("AUDIT_TEST_DB_USERNAME"), System.getenv("AUDIT_TEST_DB_PASSWORD"));
    }

    static synchronized void configure(DynamicPropertyRegistry registry, boolean runtime) {
        requireConsent();
        if (!bootstrapped) {
            try (Connection connection = admin(); var statement = connection.createStatement()) {
                statement.execute(Files.readString(Path.of("scripts/postgres/01-roles.sql")));
                statement.execute("ALTER ROLE audit_migrator PASSWORD '" + MIGRATION_PASSWORD.replace("'", "''") + "'");
                statement.execute("ALTER ROLE audit_runtime PASSWORD '" + RUNTIME_PASSWORD.replace("'", "''") + "'");
                bootstrapped = true;
            } catch (Exception exception) {
                throw new IllegalStateException("Disposable role bootstrap failed; refusing to reuse existing roles", exception);
            }
        }
        registry.add("spring.datasource.url", () -> System.getenv("AUDIT_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> runtime ? "audit_runtime" : System.getenv("AUDIT_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> runtime ? RUNTIME_PASSWORD : System.getenv("AUDIT_TEST_DB_PASSWORD"));
        registry.add("spring.flyway.url", () -> System.getenv("AUDIT_TEST_DB_URL"));
        registry.add("spring.flyway.user", () -> "audit_migrator");
        registry.add("spring.flyway.password", () -> MIGRATION_PASSWORD);
        registry.add("audit.crypto.master-key-base64", () -> MASTER_KEY);
        registry.add("audit.jwt.public-key-base64", JwtTestTokens::publicKey);
    }

    private static String random() {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        return Base64.getEncoder().encodeToString(value);
    }
}
