package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.example.auditlog.persistence.AuditReadRepository;
import org.springframework.dao.DataAccessResourceFailureException;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:securitycontract;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "audit.verification.max-concurrent=1"})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class SecurityIntegrationTest {

    private static final String EVENT = """
            {"eventType":"READ","actorId":"synthetic-actor","resourceType":"ACCOUNT","resourceId":"synthetic-resource",
             "payload":{"accountNumber":"synthetic-sensitive"}}
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private AuditReadRepository repository;

    @DynamicPropertySource
    static void signingKey(DynamicPropertyRegistry registry) {
        registry.add("audit.jwt.public-key-base64", JwtTestTokens::publicKey);
    }

    @BeforeEach
    void resetIsolatedStore() {
        reset(repository);
        jdbc.update("DELETE FROM payload_key");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("UPDATE chain_head SET last_sequence = 0, last_hash = ?", "0".repeat(64));
    }

    @Test
    void signedBearerEnablesEndToEndWriteQueryLookupAndVerification() throws Exception {
        String token = "Bearer " + JwtTestTokens.token("AUDIT_WRITE", "AUDIT_READ", "AUDIT_VERIFY");
        var created = mvc.perform(post("/audit/events").header("Authorization", token)
                        .contentType("application/json").content(EVENT))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sequence").value(1)).andReturn();
        String location = created.getResponse().getHeader("Location");
        mvc.perform(get(location).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.accountNumber").value("synthetic-sensitive"));
        mvc.perform(get("/audit/events").header("Authorization", token).param("actorId", "synthetic-actor"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(1));
        mvc.perform(get("/audit/verify").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(true));
        assertThat(created.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUDIT_WRITE", "AUDIT_READ", "AUDIT_VERIFY", "AUDIT_DOCS", "AUDIT_LIFECYCLE", "AUDIT_EXPORT", "AUDIT_COMPLIANCE"})
    void permissionMatrixDoesNotGrantOtherCapabilities(String permission) throws Exception {
        String token = "Bearer " + JwtTestTokens.token(permission);
        mvc.perform(post("/audit/events").header("Authorization", token).contentType("application/json").content(EVENT))
                .andExpect(status().is(permission.equals("AUDIT_WRITE") ? 201 : 403));
        mvc.perform(get("/audit/events").header("Authorization", token))
                .andExpect(status().is(permission.equals("AUDIT_READ") ? 200 : 403));
        mvc.perform(get("/audit/verify").header("Authorization", token))
                .andExpect(status().is(permission.equals("AUDIT_VERIFY") ? 200 : 403));
        mvc.perform(get("/v3/api-docs").header("Authorization", token))
                .andExpect(status().is(permission.equals("AUDIT_DOCS") ? 200 : 403));
    }

    @Test
    void invalidSignaturesAlgorithmsTimesIssuerAudienceAndRequiredClaimsAreRejected() throws Exception {
        List<String> invalid = new ArrayList<>();
        invalid.add("malformed-secret-token");
        invalid.add(JwtTestTokens.sign(JwtTestTokens.claims("AUDIT_READ"), JwtTestTokens.keyPair().getPrivate(), "RS256"));
        invalid.add(JwtTestTokens.sign(JwtTestTokens.claims("AUDIT_READ"), JwtTestTokens.KEYS.getPrivate(), "RS512"));
        invalid.add(JwtTestTokens.sign(JwtTestTokens.claims("AUDIT_READ"), JwtTestTokens.KEYS.getPrivate(), "HS256"));
        invalid.add(JwtTestTokens.sign(JwtTestTokens.claims("AUDIT_READ"), JwtTestTokens.KEYS.getPrivate(), "none"));
        long now = Instant.now().getEpochSecond();
        for (var change : List.of(Map.entry("iss", (Object) "urn:untrusted"), Map.entry("aud", (Object) List.of("other-service")),
                Map.entry("exp", (Object) (now - 120)), Map.entry("nbf", (Object) (now + 120)),
                Map.entry("iat", (Object) (now + 120)), Map.entry("sub", (Object) " "),
                Map.entry("permissions", (Object) "AUDIT_READ"), Map.entry("permissions", (Object) List.of(123)))) {
            var claims = JwtTestTokens.claims("AUDIT_READ");
            claims.put(change.getKey(), change.getValue());
            invalid.add(JwtTestTokens.sign(claims, JwtTestTokens.KEYS.getPrivate(), "RS256"));
        }
        for (String missing : List.of("exp", "nbf", "iat", "sub", "aud", "iss")) {
            var claims = JwtTestTokens.claims("AUDIT_READ");
            claims.remove(missing);
            invalid.add(JwtTestTokens.sign(claims, JwtTestTokens.KEYS.getPrivate(), "RS256"));
        }
        for (String token : invalid) {
            var response = mvc.perform(get("/audit/events").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("A valid bearer token is required")).andReturn().getResponse();
            assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
            assertThat(response.getContentAsString()).doesNotContain(token, "exception", "stackTrace");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isZero();
    }

    @Test
    void unknownPermissionsScopesRolesAndNonHeaderTokensNeverEscalate() throws Exception {
        var claims = JwtTestTokens.claims("ROLE_ADMIN", "SCOPE_AUDIT_WRITE", "unknown");
        claims.put("scope", "AUDIT_WRITE AUDIT_READ");
        claims.put("roles", List.of("admin"));
        String token = JwtTestTokens.sign(claims, JwtTestTokens.KEYS.getPrivate(), "RS256");
        mvc.perform(get("/audit/events").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        String valid = JwtTestTokens.token("AUDIT_READ");
        mvc.perform(get("/audit/events").param("access_token", valid)).andExpect(status().isUnauthorized());
        mvc.perform(get("/audit/events").cookie(new jakarta.servlet.http.Cookie("access_token", valid)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/audit/events").contentType("application/x-www-form-urlencoded").param("access_token", valid))
                .andExpect(status().isUnauthorized());
        claims.remove("permissions");
        mvc.perform(get("/audit/events").header("Authorization", "Bearer "
                + JwtTestTokens.sign(claims, JwtTestTokens.KEYS.getPrivate(), "RS256"))).andExpect(status().isForbidden());
    }

    @Test
    void documentationAndAssetsRequireDocsPermissionAndExposeAccurateContract() throws Exception {
        for (String path : List.of("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config",
                "/swagger-ui/index.html", "/swagger-ui/swagger-ui.css")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", "Bearer " + JwtTestTokens.token("AUDIT_READ")))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).header("Authorization", "Bearer " + JwtTestTokens.token("AUDIT_DOCS")))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + JwtTestTokens.token("AUDIT_DOCS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.WriteEventRequest").exists())
                .andExpect(jsonPath("$.paths['/audit/events'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/audit/events'].post.responses['201'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AppendReceipt"))
                .andExpect(jsonPath("$.paths['/audit/events'].get.responses['409'].content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ApiProblem"))
                .andExpect(jsonPath("$.paths['/audit/verify'].get.responses['429']").exists())
                .andExpect(jsonPath("$.paths['/audit/events'].post['x-required-permission']").value("AUDIT_WRITE"))
                .andExpect(jsonPath("$.paths['/audit/verify'].get['x-required-permission']").value("AUDIT_VERIFY"));
    }

    @Test
    void authenticatedValidationAndTamperingStillUseSafeErrorsAndIntegrityResults() throws Exception {
        String token = "Bearer " + JwtTestTokens.token("AUDIT_WRITE", "AUDIT_READ", "AUDIT_VERIFY");
        mvc.perform(post("/audit/events").header("Authorization", token).contentType("application/json")
                        .content("{\"sensitive-invalid-input\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail")
                        .value("Event must be an object with supported fields only"));
        mvc.perform(post("/audit/events").header("Authorization", token).contentType("application/json").content(EVENT))
                .andExpect(status().isCreated());
        jdbc.update("UPDATE audit_event SET actor_id = 'tampered'");
        mvc.perform(get("/audit/verify").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(false))
                .andExpect(jsonPath("$.inconsistency.type").value("INDEX_FIELD_MISMATCH"));
    }

    @Test
    void verificationConcurrencyIsBoundedAndFailureReleasesCapacity() throws Exception {
        CountDownLatch scanning = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        String token = "Bearer " + JwtTestTokens.token("AUDIT_VERIFY");
        doAnswer(invocation -> {
            scanning.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Synthetic scan did not release");
            }
            return invocation.callRealMethod();
        }).when(repository).streamAll();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var active = executor.submit(() -> mvc.perform(get("/audit/verify").header("Authorization", token))
                    .andExpect(status().isOk()).andReturn());
            try {
                assertThat(scanning.await(10, TimeUnit.SECONDS)).isTrue();
                mvc.perform(get("/audit/verify").header("Authorization", token)).andExpect(status().isTooManyRequests());
            } finally {
                release.countDown();
            }
            active.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            reset(repository);
        }
        doThrow(new DataAccessResourceFailureException("Synthetic storage failure")).when(repository).head();
        mvc.perform(get("/audit/verify").header("Authorization", token)).andExpect(status().isServiceUnavailable());
        reset(repository);
        mvc.perform(get("/audit/verify").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void mutationAndUnimplementedRoutesStayDeniedEvenWithAllPermissions() throws Exception {
        String token = "Bearer " + JwtTestTokens.token("AUDIT_WRITE", "AUDIT_READ", "AUDIT_VERIFY", "AUDIT_DOCS",
                "AUDIT_LIFECYCLE", "AUDIT_EXPORT", "AUDIT_COMPLIANCE");
        mvc.perform(put("/audit/events").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(delete("/audit/events").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(post("/audit/retention/runs").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get("/audit/exports").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/env").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
