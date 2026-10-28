package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.auditlog.audit.CanonicalJson;
import com.example.auditlog.audit.EventInput;
import com.example.auditlog.audit.PayloadProtector;
import com.example.auditlog.persistence.AuditAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "audit.retention.window=P1D")
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@Import(LifecycleTestClock.class)
class PostgresLifecycleRuntimeIT {

    @Autowired
    private JdbcTemplate runtime;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private AuditAppender appender;
    @Autowired
    private PayloadProtector protector;
    @Autowired
    private CanonicalJson canonical;

    @DynamicPropertySource
    static void setup(DynamicPropertyRegistry registry) {
        PostgresFixture.configure(registry, true);
    }

    @BeforeEach
    void resetOnlyDisposableDatabaseWithExplicitAdminConsent() throws Exception {
        try (var connection = PostgresFixture.admin(); var statement = connection.createStatement()) {
            for (String table : List.of("payload_redaction", "event_archive", "lifecycle_action", "payload_key", "audit_event")) {
                statement.executeUpdate("DELETE FROM " + table);
            }
            statement.executeUpdate("UPDATE chain_head SET last_sequence = 0, last_hash = '" + "0".repeat(64) + "'");
        }
    }

    @Test
    void scopedDefinerFunctionAllowsAuditedApiErasureButRefusesUnprovenCalls() throws Exception {
        UUID event = seed();
        assertThat(runtime.queryForObject("SELECT current_user", String.class)).isEqualTo("audit_runtime");
        assertThatThrownBy(() -> runtime.queryForObject("SELECT public.erase_audit_field_key(?, ?, ?)", Integer.class,
                event, "/accountNumber", UUID.randomUUID())).isInstanceOf(DataAccessException.class);
        assertThat(runtime.queryForObject("SELECT COUNT(*) FROM payload_key", Integer.class)).isEqualTo(1);
        UUID operation = UUID.randomUUID();
        byte[] body = canonical.bytes(Map.of("operationId", operation.toString(), "reason", "synthetic-privacy",
                "approvalReference", "synthetic-approved", "confirmation", "ERASE_FIELD_KEYS", "paths", List.of("/accountNumber")));
        mvc.perform(post("/audit/events/" + event + "/redactions").header("Authorization", bearer("AUDIT_LIFECYCLE"))
                        .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(1));
        assertThat(runtime.queryForObject("SELECT COUNT(*) FROM payload_key", Integer.class)).isZero();
        assertThatThrownBy(() -> runtime.execute("DELETE FROM payload_key")).isInstanceOf(DataAccessException.class);
        mvc.perform(get("/audit/events/" + event).header("Authorization", bearer("AUDIT_READ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.redactedPaths[0]").value("/accountNumber"));
        mvc.perform(get("/audit/verify").header("Authorization", bearer("AUDIT_VERIFY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intact").value(true));
    }

    @Test
    void actualRuntimeArchivesThroughApiAndCannotAlterLifecycleProof() throws Exception {
        UUID event = seed();
        byte[] body = canonical.bytes(Map.of("operationId", UUID.randomUUID().toString(), "reason", "synthetic-policy"));
        mvc.perform(post("/audit/retention/runs").header("Authorization", bearer("AUDIT_LIFECYCLE"))
                        .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(1));
        mvc.perform(get("/audit/events/" + event).header("Authorization", bearer("AUDIT_READ"))).andExpect(status().isNotFound());
        mvc.perform(get("/audit/events/" + event).param("includeArchived", "true")
                        .header("Authorization", bearer("AUDIT_READ", "AUDIT_LIFECYCLE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        for (String forbidden : List.of("DELETE FROM event_archive", "DELETE FROM lifecycle_action", "DELETE FROM payload_redaction",
                "UPDATE event_archive SET archived_at = CURRENT_TIMESTAMP")) {
            assertThatThrownBy(() -> runtime.execute(forbidden)).isInstanceOf(DataAccessException.class);
        }
        mvc.perform(get("/audit/verify").header("Authorization", bearer("AUDIT_VERIFY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intact").value(true));
    }

    private UUID seed() {
        UUID id = UUID.randomUUID();
        var at = LifecycleTestClock.NOW.minus(Duration.ofDays(2));
        Map<String, Object> payload = Map.of("accountNumber", "synthetic-sensitive");
        return appender.append(id, at, new EventInput("READ", "actor", "ACCOUNT", "resource", payload, at),
                protector.protect(id, payload)).eventId();
    }

    private String bearer(String... permissions) {
        return "Bearer " + JwtTestTokens.token(permissions);
    }
}
