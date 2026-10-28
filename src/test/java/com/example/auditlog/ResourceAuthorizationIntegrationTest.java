package com.example.auditlog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:resourcescope;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "audit.retention.window=P1D"})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ResourceAuthorizationIntegrationTest extends ResourceAuthorizationContract {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("audit.jwt.public-key-base64", JwtTestTokens::publicKey);
        registry.add("audit.export.private-key-base64", ExportTestKeys::privateKey);
    }
}
