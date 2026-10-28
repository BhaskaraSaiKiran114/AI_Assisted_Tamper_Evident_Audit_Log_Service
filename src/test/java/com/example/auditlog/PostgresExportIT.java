package com.example.auditlog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"audit.retention.window=P1D", "audit.export.max-concurrent=1", "audit.export.max-records=20",
        "audit.export.max-selected=2", "audit.export.max-bytes=4096"})
@ActiveProfiles("postgres")
@Import(LifecycleTestClock.class)
class PostgresExportIT extends ExportContract {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        PostgresFixture.configure(registry, false);
        registry.add("audit.export.private-key-base64", ExportTestKeys::privateKey);
    }
}
