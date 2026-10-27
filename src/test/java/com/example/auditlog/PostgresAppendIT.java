package com.example.auditlog;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("postgres")
class PostgresAppendIT extends AppendContract {

    @BeforeAll
    static void requireDisposableDatabaseConsent() {
        String url = System.getenv("AUDIT_TEST_DB_URL");
        if (!"yes".equals(System.getenv("AUDIT_TEST_DB_DESTRUCTIVE_OK")) || url == null
                || !url.matches("jdbc:postgresql://[^/]+/audit_m2_test(?:\\?.*)?")) {
            throw new IllegalStateException("Reference tests require explicit consent and a disposable audit_m2_test database");
        }
    }

    @DynamicPropertySource
    static void configureReferenceStore(DynamicPropertyRegistry registry) {
        requireDisposableDatabaseConsent();
        registry.add("spring.datasource.url", () -> System.getenv("AUDIT_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("AUDIT_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("AUDIT_TEST_DB_PASSWORD"));
        byte[] master = new byte[32];
        new SecureRandom().nextBytes(master);
        registry.add("audit.crypto.master-key-base64", () -> Base64.getEncoder().encodeToString(master));
    }
}
