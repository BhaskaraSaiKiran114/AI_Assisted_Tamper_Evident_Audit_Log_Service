package com.example.auditlog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"audit.retention.window=P1D", "audit.retention.batch-size=2"})
@ActiveProfiles("postgres")
@Import(LifecycleTestClock.class)
class PostgresLifecycleIT extends LifecycleContract {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        PostgresFixture.configure(registry, false);
    }
}
