package com.example.auditlog;

import java.util.List;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"audit.retention.window=P1D", "audit.report.max-scanned=5"})
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@Import(LifecycleTestClock.class)
class PostgresAccessReportRuntimeIT extends AccessReportSecurityContract {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        PostgresFixture.configure(registry, true);
        registry.add("audit.jwt.public-key-base64", JwtTestTokens::publicKey);
    }

    @Override
    protected void clean() throws Exception {
        try (var connection = PostgresFixture.admin(); var statement = connection.createStatement()) {
            for (String table : List.of("payload_redaction", "event_archive", "lifecycle_action", "payload_key", "audit_event")) {
                statement.executeUpdate("DELETE FROM " + table);
            }
            statement.executeUpdate("UPDATE chain_head SET last_sequence = 0, last_hash = '" + "0".repeat(64) + "'");
        }
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("audit_runtime");
    }
}
