package com.example.auditlog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:reportcontract;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "audit.retention.window=P1D", "audit.report.max-scanned=5", "audit.report.max-concurrent=1"})
@ActiveProfiles("h2")
@Import(LifecycleTestClock.class)
class AccessReportIntegrationTest extends AccessReportContract {
}
