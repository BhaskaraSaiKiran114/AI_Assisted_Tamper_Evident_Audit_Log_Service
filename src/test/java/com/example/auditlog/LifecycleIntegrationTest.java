package com.example.auditlog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:lifecyclecontract;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "audit.retention.window=P1D", "audit.retention.batch-size=2"})
@ActiveProfiles("h2")
@Import(LifecycleTestClock.class)
class LifecycleIntegrationTest extends LifecycleContract {
}
