package com.example.auditlog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("postgres")
class PostgresReadVerificationIT extends ReadVerificationContract {

    @DynamicPropertySource
    static void configureReferenceStore(DynamicPropertyRegistry registry) {
        PostgresAppendIT.configureReferenceStore(registry);
    }
}
