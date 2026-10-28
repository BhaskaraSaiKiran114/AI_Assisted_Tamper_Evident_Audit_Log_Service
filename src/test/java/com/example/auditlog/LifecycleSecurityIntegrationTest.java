package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.auditlog.audit.AuditIngestionService;
import com.example.auditlog.audit.CanonicalJson;
import com.example.auditlog.audit.EventInput;
import com.example.auditlog.audit.PayloadProtector;
import com.example.auditlog.audit.StoredJson;
import com.example.auditlog.persistence.AuditAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:lifecyclesecurity;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "audit.retention.window=P1D"})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Import(LifecycleTestClock.class)
class LifecycleSecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuditAppender appender;
    @Autowired
    private PayloadProtector protector;
    @Autowired
    private CanonicalJson canonical;
    @Autowired
    private AuditIngestionService ingestion;
    @Autowired
    private StoredJson json;

    @DynamicPropertySource
    static void signingKey(DynamicPropertyRegistry registry) {
        registry.add("audit.jwt.public-key-base64", JwtTestTokens::publicKey);
    }

    @BeforeEach
    void clearDedicatedFixture() {
        jdbc.update("DELETE FROM payload_redaction");
        jdbc.update("DELETE FROM event_archive");
        jdbc.update("DELETE FROM lifecycle_action");
        jdbc.update("DELETE FROM payload_key");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("UPDATE chain_head SET last_sequence = 0, last_hash = ?", "0".repeat(64));
    }

    @Test
    void lifecycleRoutesRejectAnonymousAndOtherRolesBeforeAnyMutation() throws Exception {
        String retention = retention(UUID.randomUUID());
        String redaction = redaction(UUID.randomUUID(), "privacy");
        for (String route : List.of("/audit/retention/runs", "/audit/events/" + UUID.randomUUID() + "/redactions")) {
            mvc.perform(post(route).contentType("application/json").content(route.endsWith("/runs") ? retention : redaction))
                    .andExpect(status().isUnauthorized());
            for (String role : List.of("AUDIT_WRITE", "AUDIT_READ", "AUDIT_VERIFY", "AUDIT_DOCS")) {
                mvc.perform(post(route).header("Authorization", bearer(role)).contentType("application/json")
                                .content(route.endsWith("/runs") ? retention : redaction)).andExpect(status().isForbidden());
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_action", Integer.class)).isZero();
    }

    @Test
    void archiveOptInRequiresBothReadAndLifecycleAndDefaultLookupHidesArchivedEvents() throws Exception {
        UUID event = seedOld();
        mvc.perform(post("/audit/retention/runs").header("Authorization", bearer("AUDIT_LIFECYCLE"))
                        .contentType("application/json").content(retention(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.affectedCount").value(1));
        mvc.perform(get("/audit/events/" + event).header("Authorization", bearer("AUDIT_READ")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/audit/events").param("eventType", "READ").header("Authorization", bearer("AUDIT_READ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(0));
        mvc.perform(get("/audit/events").param("includeArchived", "true").header("Authorization", bearer("AUDIT_READ")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/audit/events/" + event).param("includeArchived", "true").header("Authorization", bearer("AUDIT_LIFECYCLE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/audit/events/" + event).param("includeArchived", "true")
                        .header("Authorization", bearer("AUDIT_READ", "AUDIT_LIFECYCLE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        mvc.perform(get("/audit/events").param("includeArchived", "yes")
                        .header("Authorization", bearer("AUDIT_READ", "AUDIT_LIFECYCLE"))).andExpect(status().isBadRequest());
    }

    @Test
    void realBearerRedactionIsAuditedAndRetryIsSafe() throws Exception {
        UUID event = seedOld();
        UUID operation = UUID.randomUUID();
        var first = mvc.perform(post("/audit/events/" + event + "/redactions").header("Authorization", bearer("AUDIT_LIFECYCLE"))
                        .contentType("application/json").content(redaction(operation, "privacy")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.affectedCount").value(1)).andReturn();
        var again = mvc.perform(post("/audit/events/" + event + "/redactions").header("Authorization", bearer("AUDIT_LIFECYCLE"))
                        .contentType("application/json").content(redaction(operation, "privacy")))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.object(again.getResponse().getContentAsByteArray()).get("auditEventId"))
                .isEqualTo(json.object(first.getResponse().getContentAsByteArray()).get("auditEventId"));
        mvc.perform(post("/audit/events/" + event + "/redactions").header("Authorization", bearer("AUDIT_LIFECYCLE"))
                        .contentType("application/json").content(redaction(operation, "changed"))).andExpect(status().isConflict());
        mvc.perform(get("/audit/events/" + event).header("Authorization", bearer("AUDIT_READ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.redactedPaths[0]").value("/accountNumber"))
                .andExpect(jsonPath("$.payload.accountNumber['$auditRedacted'].operationId").value(operation.toString()));
        mvc.perform(get("/audit/verify").header("Authorization", bearer("AUDIT_VERIFY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intact").value(true));
        assertThat(jdbc.queryForObject("SELECT actor_id FROM lifecycle_action", String.class)).isEqualTo("synthetic-producer");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(2);
    }

    @Test
    void explicitErasureConfirmationAndBoundedRequestsAreMandatory() throws Exception {
        UUID event = seedOld();
        String route = "/audit/events/" + event + "/redactions";
        mvc.perform(post(route).header("Authorization", bearer("AUDIT_LIFECYCLE")).contentType("application/json")
                        .content(redaction(UUID.randomUUID(), "privacy").replace("ERASE_FIELD_KEYS", "false")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(route).header("Authorization", bearer("AUDIT_LIFECYCLE")).contentType("application/json")
                        .content(new byte[16 * 1024 + 1])).andExpect(status().isPayloadTooLarge());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payload_key", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_action", Integer.class)).isZero();
    }

    @Test
    void externalWritesCannotImpersonateAdministrativeEventsOrRedactionMarkers() throws Exception {
        var body = Map.of("eventType", "AUDIT_INTERNAL_REDACTION", "actorId", "actor", "resourceType", "ACCOUNT",
                "resourceId", "resource", "payload", Map.of());
        mvc.perform(post("/audit/events").header("Authorization", bearer("AUDIT_WRITE")).contentType("application/json")
                        .content(canonical.bytes(body))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isZero();
        var marker = Map.of("eventType", "READ", "actorId", "actor", "resourceType", "ACCOUNT", "resourceId", "resource",
                "payload", Map.of("$auditRedacted", Map.of()));
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> ingestion.append(canonical.bytes(marker))))
                .isInstanceOf(com.example.auditlog.audit.InvalidEventException.class);
    }

    @Test
    void openApiListsLifecycleBodiesPermissionsAndArchiveOptIn() throws Exception {
        mvc.perform(get("/v3/api-docs").header("Authorization", bearer("AUDIT_DOCS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/audit/retention/runs'].post['x-required-permission']").value("AUDIT_LIFECYCLE"))
                .andExpect(jsonPath("$.paths['/audit/events/{eventId}/redactions'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/audit/events'].get['x-include-archived-permission']").value("AUDIT_LIFECYCLE"));
    }

    private UUID seedOld() {
        UUID id = UUID.randomUUID();
        var at = LifecycleTestClock.NOW.minus(Duration.ofDays(2));
        Map<String, Object> payload = Map.of("accountNumber", "synthetic-account");
        return appender.append(id, at, new EventInput("READ", "actor", "ACCOUNT", "resource", payload, at),
                protector.protect(id, payload)).eventId();
    }

    private String retention(UUID id) {
        return new String(canonical.bytes(Map.of("operationId", id.toString(), "reason", "synthetic-policy")), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String redaction(UUID id, String reason) {
        return new String(canonical.bytes(Map.of("operationId", id.toString(), "reason", reason,
                "approvalReference", "synthetic-approved", "confirmation", "ERASE_FIELD_KEYS", "paths", List.of("/accountNumber"))),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private String bearer(String... permissions) {
        return "Bearer " + JwtTestTokens.token(permissions);
    }
}
