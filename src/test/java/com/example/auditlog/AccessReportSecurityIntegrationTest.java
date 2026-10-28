package com.example.auditlog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:reportsecurity;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "audit.retention.window=P1D", "audit.report.max-scanned=5"})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Import(LifecycleTestClock.class)
class AccessReportSecurityIntegrationTest extends AccessReportSecurityContract {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("audit.jwt.public-key-base64", JwtTestTokens::publicKey);
    }
}
