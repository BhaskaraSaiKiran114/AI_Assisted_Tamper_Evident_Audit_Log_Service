package com.example.auditlog;

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
        PostgresFixture.requireConsent();
    }

    @DynamicPropertySource
    static void configureReferenceStore(DynamicPropertyRegistry registry) {
        PostgresFixture.configure(registry, false);
    }
}
