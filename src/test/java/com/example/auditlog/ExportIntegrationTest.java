package com.example.auditlog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:exportcontract;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "audit.retention.window=P1D", "audit.export.max-concurrent=1", "audit.export.max-records=20",
        "audit.export.max-selected=2", "audit.export.max-bytes=4096"})
@ActiveProfiles("h2")
@Import(LifecycleTestClock.class)
class ExportIntegrationTest extends ExportContract {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("audit.export.private-key-base64", ExportTestKeys::privateKey);
    }
}
