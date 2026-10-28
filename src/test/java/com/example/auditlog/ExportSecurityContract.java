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
import com.example.auditlog.export.ExportBundle;
import com.example.auditlog.persistence.AuditAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

abstract class ExportSecurityContract {

    @Autowired
    private MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private AuditAppender appender;
    @Autowired
    private PayloadProtector protector;

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
    void onlyExplicitExportPermissionOpensRoute() throws Exception {
        mvc.perform(get("/audit/exports").param("actorId", "chosen")).andExpect(status().isUnauthorized());
        for (String permission : List.of("AUDIT_WRITE", "AUDIT_READ", "AUDIT_VERIFY", "AUDIT_DOCS", "AUDIT_LIFECYCLE", "AUDIT_COMPLIANCE")) {
            mvc.perform(get("/audit/exports").param("actorId", "chosen").header("Authorization", bearer(permission)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/audit/events").header("Authorization", bearer("AUDIT_EXPORT"))).andExpect(status().isForbidden());
    }

    @Test
    void signedZipWorksWithExportAloneAndHasNoPlaintextKeysOrWriteSideEffect() throws Exception {
        UUID id = seed();
        byte[] body = mvc.perform(get("/audit/exports").param("actorId", "chosen").header("Authorization", bearer("AUDIT_EXPORT")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"audit-export.zip\""))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ExportBundle.verify(body, ExportTestKeys.PAIR.getPublic()).selectedIds()).containsExactly(id);
        assertThat(new String(ExportContract.entry(body, "records.bin"), java.nio.charset.StandardCharsets.ISO_8859_1))
                .contains("$auditProtected").doesNotContain("synthetic-sensitive");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
    }

    @Test
    void archivedExportRequiresExplicitOptInAndAdditionalLifecyclePermission() throws Exception {
        UUID id = seed();
        mvc.perform(post("/audit/retention/runs").header("Authorization", bearer("AUDIT_LIFECYCLE"))
                        .contentType("application/json").content("{\"operationId\":\"" + UUID.randomUUID() + "\",\"reason\":\"synthetic\"}"))
                .andExpect(status().isOk());
        byte[] defaultBody = mvc.perform(get("/audit/exports").param("actorId", "chosen").header("Authorization", bearer("AUDIT_EXPORT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(ExportBundle.verify(defaultBody, ExportTestKeys.PAIR.getPublic()).selectedIds()).isEmpty();
        mvc.perform(get("/audit/exports").param("actorId", "chosen").param("includeArchived", "true")
                .header("Authorization", bearer("AUDIT_EXPORT"))).andExpect(status().isForbidden());
        byte[] included = mvc.perform(get("/audit/exports").param("actorId", "chosen").param("includeArchived", "true")
                .header("Authorization", bearer("AUDIT_EXPORT", "AUDIT_LIFECYCLE")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(ExportBundle.verify(included, ExportTestKeys.PAIR.getPublic()).selectedIds()).containsExactly(id);
    }

    @Test
    void strictParametersRejectBeforeDatabaseExportAndErrorDoesNotEchoInput() throws Exception {
        String token = bearer("AUDIT_EXPORT");
        mvc.perform(get("/audit/exports").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(get("/audit/exports").param("actorId", "a", "b").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(get("/audit/exports").param("actorId", "a").param("includeArchived", "TRUE").header("Authorization", token))
                .andExpect(status().isBadRequest());
        String response = mvc.perform(get("/audit/exports").param("unexpected-sensitive-value", "do-not-echo")
                        .header("Authorization", token)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("do-not-echo", "unexpected-sensitive-value");
    }

    @Test
    void protectedOpenApiSpecDescribesBinaryExportPermissionsAndBounds() throws Exception {
        mvc.perform(get("/v3/api-docs").header("Authorization", bearer("AUDIT_EXPORT"))).andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs").header("Authorization", bearer("AUDIT_DOCS"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/audit/exports'].get['x-required-permission']").value("AUDIT_EXPORT"))
                .andExpect(jsonPath("$.paths['/audit/exports'].get['x-include-archived-permission']").value("AUDIT_LIFECYCLE"))
                .andExpect(jsonPath("$.paths['/audit/exports'].get.responses['200'].content['application/zip'].schema.format").value("binary"))
                .andExpect(jsonPath("$.paths['/audit/exports'].get.responses['413']").exists())
                .andExpect(jsonPath("$.paths['/audit/exports'].get.responses['429']").exists());
    }

    private UUID seed() {
        UUID id = UUID.randomUUID();
        var payload = Map.<String, Object>of("accountNumber", "synthetic-sensitive");
        appender.append(id, Instant.parse("2020-01-01T00:00:00Z"),
                new EventInput("READ", "chosen", "ACCOUNT", "r", payload, null), protector.protect(id, payload));
        return id;
    }

    private String bearer(String... permissions) {
        return "Bearer " + JwtTestTokens.token(permissions);
    }
}
