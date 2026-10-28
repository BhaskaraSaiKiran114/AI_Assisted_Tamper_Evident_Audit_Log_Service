package com.example.auditlog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:localswagger;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "audit.swagger.local-public=true", "server.address=127.0.0.1"})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class LocalSwaggerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("audit.jwt.public-key-base64", JwtTestTokens::publicKey);
    }

    @Test
    void documentationAndAssetsLoadWithoutTokenAndRetainBearerApiContract() throws Exception {
        for (String path : List.of("/swagger-ui/index.html", "/swagger-ui/swagger-ui.css", "/swagger-ui/swagger-ui-bundle.js",
                "/swagger-ui/swagger-initializer.js", "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/v3/api-docs")).andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        mvc.perform(post("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void businessRoutesStillRequireJwtAndTheirExactPermissions() throws Exception {
        for (String path : List.of("/audit/events", "/audit/verify", "/audit/exports", "/audit/compliance/access-report")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/audit/events").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(get("/audit/events").header("Authorization", "Bearer " + JwtTestTokens.token("AUDIT_DOCS")))
                .andExpect(status().isForbidden());
        String token = "Bearer " + JwtTestTokens.token("AUDIT_WRITE", "AUDIT_READ");
        mvc.perform(post("/audit/events").header("Authorization", token).contentType("application/json")
                .content("{\"eventType\":\"READ\",\"actorId\":\"synthetic\",\"resourceType\":\"ACCOUNT\",\"resourceId\":\"test\",\"payload\":{}}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/audit/events").header("Authorization", token)).andExpect(status().isOk());
    }
}
