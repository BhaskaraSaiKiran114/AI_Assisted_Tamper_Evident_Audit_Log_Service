package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class FoundationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Test
    void migrationInitializesExactlyOneGenesisHead() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chain_head", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_sequence FROM chain_head WHERE chain_id = 1", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT last_hash FROM chain_head WHERE chain_id = 1", String.class))
                .isEqualTo("0".repeat(64));
    }

    @Test
    void healthIsAvailableWithoutExposingDatabaseDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void auditAndDocumentationAccessAreDeniedUntilSecurityMilestone() throws Exception {
        mvc.perform(get("/audit/events")).andExpect(status().isForbidden());
        mvc.perform(post("/audit/events").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isForbidden());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isForbidden());
    }
}
