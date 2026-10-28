package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.auditlog.audit.EventInput;
import com.example.auditlog.audit.PayloadProtector;
import com.example.auditlog.audit.StoredJson;
import com.example.auditlog.persistence.AuditAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

abstract class AccessReportSecurityContract {

    @Autowired
    private MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private AuditAppender appender;
    @Autowired
    private PayloadProtector protector;
    @Autowired
    private StoredJson json;

    @BeforeEach
    void resetStore() throws Exception {
        clean();
    }

    protected void clean() throws Exception {
        for (String table : List.of("payload_redaction", "event_archive", "lifecycle_action", "payload_key", "audit_event")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("UPDATE chain_head SET last_sequence = 0, last_hash = ?", "0".repeat(64));
    }

    @Test
    void compliancePermissionIsIndependentOfEveryOtherAuthority() throws Exception {
        mvc.perform(request()).andExpect(status().isUnauthorized());
        for (String permission : List.of("AUDIT_READ", "AUDIT_WRITE", "AUDIT_VERIFY", "AUDIT_DOCS", "AUDIT_LIFECYCLE", "AUDIT_EXPORT")) {
            mvc.perform(request().header("Authorization", bearer(permission))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/audit/events").header("Authorization", bearer("AUDIT_COMPLIANCE"))).andExpect(status().isForbidden());
        mvc.perform(get("/audit/exports").param("actorId", "a").header("Authorization", bearer("AUDIT_COMPLIANCE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportIsMetadataOnlyAndHasNoReadDecryptionOrWriteSideEffect() throws Exception {
        UUID target = seed("SUCCESS");
        String response = mvc.perform(request().header("Authorization", bearer("AUDIT_COMPLIANCE")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.reportVersion").value("access-report-v1"))
                .andExpect(jsonPath("$.entries[0].eventId").value(target.toString()))
                .andExpect(jsonPath("$.entries[0].actorId").value("synthetic-actor"))
                .andExpect(jsonPath("$.entries[0].accountId").value("synthetic-account"))
                .andExpect(jsonPath("$.entries[0].outcome").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("synthetic-sensitive", "do-not-expose-payload", "$auditProtected", "wrapped_key", "payload");
        var body = json.object(response.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var entry = (Map<?, ?>) ((List<?>) body.get("entries")).getFirst();
        assertThat(entry.keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder(
                "eventId", "sequence", "actorId", "accountId", "action", "outcome", "occurredAt", "archived");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
    }

    @Test
    void archivedReportsNeedExplicitOptInAndLifecyclePermission() throws Exception {
        seed("DENIED");
        mvc.perform(post("/audit/retention/runs").header("Authorization", bearer("AUDIT_LIFECYCLE"))
                        .contentType("application/json").content("{\"operationId\":\"" + UUID.randomUUID() + "\",\"reason\":\"synthetic\"}"))
                .andExpect(status().isOk());
        mvc.perform(request().header("Authorization", bearer("AUDIT_COMPLIANCE"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isEmpty());
        mvc.perform(request().param("includeArchived", "true").header("Authorization", bearer("AUDIT_COMPLIANCE")))
                .andExpect(status().isForbidden());
        mvc.perform(request().param("includeArchived", "true").header("Authorization", bearer("AUDIT_COMPLIANCE", "AUDIT_LIFECYCLE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].archived").value(true))
                .andExpect(jsonPath("$.entries[0].outcome").value("DENIED"));
    }

    @Test
    void boundsAndScanLimitFailWithoutEchoingSourcePayload() throws Exception {
        String token = bearer("AUDIT_COMPLIANCE");
        mvc.perform(get("/audit/compliance/access-report").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(request().param("includePayload", "true").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(request().param("actorId", "a", "b").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(get("/audit/compliance/access-report").param("from", "2026-10-01T00:00:00Z")
                .param("to", "2026-12-01T00:00:00Z").header("Authorization", token)).andExpect(status().isBadRequest());
        for (int index = 0; index < 6; index++) {
            seed("UNKNOWN");
        }
        String response = mvc.perform(request().header("Authorization", token)).andExpect(status().isPayloadTooLarge())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("synthetic-sensitive", "do-not-expose-payload", "entries");
    }

    @Test
    void protectedOpenApiDefinesCompliancePermissionFiltersAndSafeResponse() throws Exception {
        mvc.perform(get("/v3/api-docs").header("Authorization", bearer("AUDIT_COMPLIANCE"))).andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs").header("Authorization", bearer("AUDIT_DOCS"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/audit/compliance/access-report'].get['x-required-permission']").value("AUDIT_COMPLIANCE"))
                .andExpect(jsonPath("$.paths['/audit/compliance/access-report'].get['x-include-archived-permission']").value("AUDIT_LIFECYCLE"))
                .andExpect(jsonPath("$.paths['/audit/compliance/access-report'].get.responses['413']").exists())
                .andExpect(jsonPath("$.paths['/audit/compliance/access-report'].get.responses['429']").exists())
                .andExpect(jsonPath("$.paths['/audit/compliance/access-report'].get.responses['409']").exists());
    }

    private MockHttpServletRequestBuilder request() {
        return get("/audit/compliance/access-report").param("from", "2026-10-01T00:00:00Z").param("to", "2026-10-02T00:00:00Z");
    }

    private UUID seed(String outcome) {
        UUID id = UUID.randomUUID();
        var payload = Map.<String, Object>of("outcome", outcome, "accountNumber", "synthetic-sensitive", "ordinary", "do-not-expose-payload");
        appender.append(id, Instant.parse("2020-01-01T00:00:00Z"),
                new EventInput("READ", "synthetic-actor", "CLIENT_ACCOUNT", "synthetic-account", payload, AccessReportContract.FROM),
                protector.protect(id, payload));
        return id;
    }

    private String bearer(String... permissions) {
        return "Bearer " + JwtTestTokens.token(permissions);
    }
}
