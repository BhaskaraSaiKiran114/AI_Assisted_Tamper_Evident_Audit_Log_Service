package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.auditlog.audit.CanonicalJson;
import com.example.auditlog.audit.EventInput;
import com.example.auditlog.audit.PayloadProtector;
import com.example.auditlog.audit.StoredJson;
import com.example.auditlog.export.ExportBundle;
import com.example.auditlog.persistence.AuditAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Same permissions, different issuer-assigned resources; exercised on H2 and PostgreSQL runtime role. */
abstract class ResourceAuthorizationContract {

    private static final String[] PERMISSIONS = {"AUDIT_READ", "AUDIT_WRITE", "AUDIT_EXPORT", "AUDIT_COMPLIANCE",
        "AUDIT_LIFECYCLE", "AUDIT_VERIFY", "AUDIT_DOCS"};
    @Autowired private MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired private AuditAppender appender;
    @Autowired private PayloadProtector protector;
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void prepare() throws Exception {
        clean();
        alice = seed("alice-account", "alice-secret");
        seed("alice-account", "alice-secret-2");
        bob = seed("bob-account", "bob-secret");
    }

    protected void clean() throws Exception {
        for (String table : List.of("payload_redaction", "event_archive", "lifecycle_action", "payload_key", "audit_event")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("UPDATE chain_head SET last_sequence = 0, last_hash = ?", "0".repeat(64));
    }

    @Test
    void sameRoleCannotGuessAnotherResourceOrReadItsDecryptedPayload() throws Exception {
        mvc.perform(get("/audit/events/" + alice).header("Authorization", scoped("alice", "alice-account")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payload.accountNumber").value("alice-secret"));
        String body = mvc.perform(get("/audit/events/" + bob).header("Authorization", scoped("alice", "alice-account")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("bob-secret", "bob-account");
        mvc.perform(get("/audit/events/" + alice).header("Authorization", scoped("bob", "bob-account")))
                .andExpect(status().isNotFound());
    }

    @Test
    void sqlScopeIntersectsOptionalFiltersAndAppliesBeforePagination() throws Exception {
        String token = scoped("alice", "alice-account");
        mvc.perform(get("/audit/events").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(2));
        mvc.perform(get("/audit/events").param("resourceId", "bob-account").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(0));
        mvc.perform(get("/audit/events").header("Authorization", scoped("bob", "bob-account")).param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events[0].eventId").value(bob.toString()))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        var wrongType = scopedClaims("alice", "alice-account");
        wrongType.put("audit_resources", List.of(Map.of("resourceType", "ACCOUNT", "resourceId", "alice-account")));
        mvc.perform(get("/audit/events/" + alice).header("Authorization", bearer(wrongType))).andExpect(status().isNotFound());
    }

    @Test
    void cursorsRejectDifferentSubjectsAndChangedAssignmentsEvenWithSameFilters() throws Exception {
        String cursor = cursor("/audit/events", Map.of("limit", "1"), scoped("alice", "alice-account"));
        mvc.perform(get("/audit/events").param("limit", "1").param("cursor", cursor)
                .header("Authorization", scoped("another-subject", "alice-account"))).andExpect(status().isBadRequest());
        mvc.perform(get("/audit/events").param("limit", "1").param("cursor", cursor)
                .header("Authorization", scoped("alice", "bob-account"))).andExpect(status().isBadRequest());
        mvc.perform(get("/audit/events").param("limit", "1").param("cursor", cursor)
                .header("Authorization", scoped("alice", "alice-account")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(1));
    }

    @Test
    void exportsContainOnlyAssignedContentEvenForBroadActorFilter() throws Exception {
        byte[] zip = mvc.perform(get("/audit/exports").param("actorId", "shared-actor")
                        .header("Authorization", scoped("alice", "alice-account")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(ExportBundle.verify(zip, ExportTestKeys.PAIR.getPublic()).selectedIds()).contains(alice).doesNotContain(bob);
        byte[] guessed = mvc.perform(get("/audit/exports").param("resourceId", "bob-account")
                        .header("Authorization", scoped("alice", "alice-account")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(ExportBundle.verify(guessed, ExportTestKeys.PAIR.getPublic()).selectedIds()).isEmpty();
    }

    @Test
    void reportScopeAppliesBeforeCountingAndCursorCannotBeReused() throws Exception {
        String token = scoped("alice", "alice-account");
        mvc.perform(get("/audit/compliance/access-report").param("from", "2020-01-01T00:00:00Z")
                        .param("to", "2020-01-02T00:00:00Z").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.scannedCount").value(2));
        mvc.perform(get("/audit/compliance/access-report").param("from", "2020-01-01T00:00:00Z")
                        .param("to", "2020-01-02T00:00:00Z").param("accountId", "bob-account").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.scannedCount").value(0));
        String cursor = cursor("/audit/compliance/access-report", Map.of("from", "2020-01-01T00:00:00Z",
                "to", "2020-01-02T00:00:00Z", "limit", "1"), token);
        mvc.perform(get("/audit/compliance/access-report").param("from", "2020-01-01T00:00:00Z")
                .param("to", "2020-01-02T00:00:00Z").param("limit", "1").param("cursor", cursor)
                .header("Authorization", scoped("bob", "alice-account"))).andExpect(status().isBadRequest());
    }

    @Test
    void scopedIngestionCannotWriteOutsideAssignmentAndGlobalOperationsAreDenied() throws Exception {
        String token = scoped("alice", "alice-account");
        for (String id : List.of("alice-account", "bob-account")) {
            mvc.perform(post("/audit/events").contentType("application/json").header("Authorization", token)
                            .content(new CanonicalJson().bytes(Map.of("eventType", "READ", "actorId", "shared-actor",
                                    "resourceType", "CLIENT_ACCOUNT", "resourceId", id, "payload", Map.of()))))
                    .andExpect(status().is(id.startsWith("alice") ? 201 : 403));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(4);
        mvc.perform(get("/audit/verify").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(post("/audit/retention/runs").contentType("application/json").content("{}")
                .header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test
    void targetedRedactionChecksResourceBeforeMutationAndBeforeIdempotentReplay() throws Exception {
        String body = new String(new CanonicalJson().bytes(Map.of("operationId", UUID.randomUUID().toString(),
                "reason", "synthetic", "approvalReference", "synthetic", "confirmation", "ERASE_FIELD_KEYS",
                "paths", List.of("/accountNumber"))), java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(post("/audit/events/" + bob + "/redactions").contentType("application/json").content(body)
                .header("Authorization", scoped("alice", "alice-account"))).andExpect(status().isNotFound());
        mvc.perform(post("/audit/events/" + alice + "/redactions").contentType("application/json").content(body)
                .header("Authorization", scoped("alice", "alice-account"))).andExpect(status().isOk());
        mvc.perform(post("/audit/events/" + alice + "/redactions").contentType("application/json").content(body)
                .header("Authorization", scoped("alice", "bob-account"))).andExpect(status().isNotFound());
        mvc.perform(get("/audit/events/" + bob).header("Authorization", scoped("bob", "bob-account")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payload.accountNumber").value("bob-secret"));
    }

    @Test
    void missingAssignmentsFailClosedAndMalformedAssignmentsAreInvalidTokens() throws Exception {
        var missing = JwtTestTokens.claims(PERMISSIONS);
        missing.remove("audit_global");
        mvc.perform(get("/audit/events").header("Authorization", bearer(missing))).andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs").header("Authorization", bearer(missing))).andExpect(status().isOk());
        for (Object invalid : List.of("*", List.of("alice-account"), List.of(Map.of("resourceId", "alice-account")),
                List.of(Map.of("resourceType", "CLIENT_ACCOUNT", "resourceId", "")))) {
            var claims = new java.util.LinkedHashMap<>(missing);
            claims.put("audit_resources", invalid);
            mvc.perform(get("/audit/events").header("Authorization", bearer(claims))).andExpect(status().isUnauthorized());
        }
        missing.put("audit_global", "true");
        mvc.perform(get("/audit/events").header("Authorization", bearer(missing))).andExpect(status().isUnauthorized());
    }

    @Test
    void archiveOptInStillRequiresPermissionAndNeverExpandsResourceScope() throws Exception {
        mvc.perform(post("/audit/retention/runs").contentType("application/json")
                .content("{\"operationId\":\"" + UUID.randomUUID() + "\",\"reason\":\"synthetic\"}")
                .header("Authorization", "Bearer " + JwtTestTokens.token("AUDIT_LIFECYCLE"))).andExpect(status().isOk());
        mvc.perform(get("/audit/events").param("includeArchived", "true")
                .header("Authorization", scoped("alice", "alice-account")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(2));
        var claims = scopedClaims("alice", "alice-account");
        claims.put("permissions", List.of("AUDIT_READ"));
        mvc.perform(get("/audit/events").param("includeArchived", "true").header("Authorization", bearer(claims)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/audit/events/" + bob).param("includeArchived", "true")
                .header("Authorization", scoped("alice", "alice-account"))).andExpect(status().isNotFound());
    }

    private String cursor(String path, Map<String, String> parameters, String token) throws Exception {
        var request = get(path).header("Authorization", token);
        parameters.forEach(request::param);
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String cursor = (String) new StoredJson().object(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)).get("nextCursor");
        assertThat(cursor).isNotBlank();
        return cursor;
    }

    private UUID seed(String resource, String secret) {
        UUID id = UUID.randomUUID();
        Map<String, Object> payload = Map.of("outcome", "SUCCESS", "accountNumber", secret);
        appender.append(id, Instant.parse("2020-01-01T00:00:00Z"), new EventInput("READ", "shared-actor", "CLIENT_ACCOUNT",
                resource, payload, Instant.parse("2020-01-01T00:00:00Z")), protector.protect(id, payload));
        return id;
    }

    private Map<String, Object> scopedClaims(String subject, String account) {
        var claims = JwtTestTokens.claims(PERMISSIONS);
        claims.remove("audit_global");
        claims.put("sub", subject);
        claims.put("audit_resources", List.of(Map.of("resourceType", "CLIENT_ACCOUNT", "resourceId", account)));
        return claims;
    }

    private String scoped(String subject, String account) {
        return bearer(scopedClaims(subject, account));
    }

    private String bearer(Map<String, Object> claims) {
        return "Bearer " + JwtTestTokens.sign(claims, JwtTestTokens.KEYS.getPrivate(), "RS256");
    }
}
