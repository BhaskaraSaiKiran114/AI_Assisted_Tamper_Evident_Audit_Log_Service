package com.example.auditlog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"audit.retention.window=P1D", "audit.report.max-scanned=5", "audit.report.max-concurrent=1"})
@ActiveProfiles("postgres")
@Import(LifecycleTestClock.class)
class PostgresAccessReportIT extends AccessReportContract {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        PostgresFixture.configure(registry, false);
    }
}
