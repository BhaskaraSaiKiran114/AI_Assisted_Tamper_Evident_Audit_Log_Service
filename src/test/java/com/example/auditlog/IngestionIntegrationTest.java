package com.example.auditlog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:appendcontract;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("h2")
class IngestionIntegrationTest extends AppendContract {
}
