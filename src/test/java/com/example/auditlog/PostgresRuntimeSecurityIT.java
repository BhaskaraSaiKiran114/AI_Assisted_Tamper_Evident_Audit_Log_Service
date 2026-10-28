package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
class PostgresRuntimeSecurityIT {

    @Autowired
    private JdbcTemplate runtime;
    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void configureRuntime(DynamicPropertyRegistry registry) {
        PostgresFixture.configure(registry, true);
    }

    @BeforeEach
    void clearOnlyIsolatedFixtureWithAdminConnection() throws SQLException {
        try (var connection = PostgresFixture.admin(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM payload_key");
            statement.executeUpdate("DELETE FROM audit_event");
            statement.executeUpdate("UPDATE chain_head SET last_sequence = 0, last_hash = '" + "0".repeat(64) + "'");
        }
    }

    @Test
    void actualRuntimePoolAppendsQueriesAndVerifiesButCannotMutateHistory() throws Exception {
        assertThat(runtime.queryForObject("SELECT current_user", String.class)).isEqualTo("audit_runtime");
        String token = "Bearer " + JwtTestTokens.token("AUDIT_WRITE", "AUDIT_READ", "AUDIT_VERIFY", "AUDIT_DOCS");
        mvc.perform(post("/audit/events").header("Authorization", token).contentType("application/json")
                        .content("{\"eventType\":\"READ\",\"actorId\":\"synthetic\",\"resourceType\":\"ACCOUNT\","
                                + "\"resourceId\":\"synthetic\",\"payload\":{\"accountNumber\":\"synthetic-sensitive\"}}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/audit/events").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].payload.accountNumber").value("synthetic-sensitive"));
        mvc.perform(get("/audit/verify").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(true));
        mvc.perform(get("/v3/api-docs").header("Authorization", token)).andExpect(status().isOk());
        for (String forbidden : new String[] {"UPDATE audit_event SET actor_id = 'forged'", "DELETE FROM audit_event",
                "TRUNCATE audit_event CASCADE", "ALTER TABLE audit_event ADD COLUMN forbidden INTEGER",
                "DROP TABLE audit_event CASCADE", "CREATE TABLE public.forbidden (id INTEGER)",
                "CREATE TEMP TABLE forbidden (id INTEGER)", "DELETE FROM payload_key", "DELETE FROM chain_head",
                "SELECT * FROM flyway_schema_history", "SET ROLE audit_migrator"}) {
            assertThatThrownBy(() -> runtime.execute(forbidden)).isInstanceOf(DataAccessException.class)
                    .satisfies(exception -> assertThat(((SQLException) exception.getCause()).getSQLState()).isEqualTo("42501"));
        }
        assertThat(runtime.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
    }

    @Test
    void migrationsOwnObjectsAndRuntimeHasNoAdministrativeOrPublicGrantEscape() throws Exception {
        assertThat(runtime.queryForObject("SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls "
                + "FROM pg_roles WHERE rolname = current_user", Boolean.class)).isFalse();
        try (var connection = PostgresFixture.admin(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT tableowner FROM pg_tables WHERE schemaname = 'public' "
                    + "AND tablename IN ('audit_event', 'payload_key', 'chain_head', 'flyway_schema_history')")) {
                int count = 0;
                while (result.next()) {
                    assertThat(result.getString(1)).isEqualTo("audit_migrator");
                    count++;
                }
                assertThat(count).isEqualTo(4);
            }
        }
        assertThat(runtime.queryForObject("SELECT has_table_privilege(current_user, 'audit_event', 'UPDATE')", Boolean.class))
                .isFalse();
        assertThat(runtime.queryForObject("SELECT has_table_privilege(current_user, 'audit_event', 'DELETE')", Boolean.class))
                .isFalse();
    }
}
