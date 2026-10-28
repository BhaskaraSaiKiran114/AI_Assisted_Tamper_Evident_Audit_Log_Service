package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.auditlog.api.AuditWriteController;
import com.example.auditlog.api.EventErrorHandler;
import com.example.auditlog.audit.AppendReceipt;
import com.example.auditlog.audit.AuditIngestionService;
import com.example.auditlog.audit.CanonicalJson;
import com.example.auditlog.audit.EventInput;
import com.example.auditlog.audit.PayloadProtector.ProtectedPayload;
import com.example.auditlog.persistence.AuditAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

abstract class AppendContract {

    @Autowired
    private AuditIngestionService ingestion;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CanonicalJson canonical;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private AuditAppender appender;

    private static final byte[] EVENT = ("{\"eventType\":\"USER_LOGIN\",\"actorId\":\"actor-1\","
            + "\"resourceType\":\"CLIENT_ACCOUNT\",\"resourceId\":\"synthetic-1\","
            + "\"payload\":{\"accountNumber\":\"synthetic-secret\",\"count\":1}}")
            .getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void resetIsolatedTestStore() {
        jdbc.update("DELETE FROM payload_redaction");
        jdbc.update("DELETE FROM event_archive");
        jdbc.update("DELETE FROM lifecycle_action");
        jdbc.update("DELETE FROM payload_key");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("UPDATE chain_head SET last_sequence = 0, last_hash = ? WHERE chain_id = 1", "0".repeat(64));
    }

    @Test
    void fixedEnvelopeMatchesIndependentGoldenHash() {
        var receipt = appender.append(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                java.time.Instant.parse("2026-10-28T00:00:00Z"),
                new EventInput("LOGIN", "actor", "ACCOUNT", "resource", Map.of("n", 1),
                        java.time.Instant.parse("2020-01-01T00:00:00.123456Z")),
                new ProtectedPayload(Map.of("n", 1), Map.of()));
        assertThat(receipt.recordHash()).isEqualTo("f5859b69d733ec8302f8ee715377907cea15134865984d2f38ee59f29ad363bb");
    }

    @Test
    void appendsGenesisAndSuccessorWithCanonicalStoredContentAndWrappedKey() {
        AppendReceipt first = ingestion.append(EVENT);
        AppendReceipt second = ingestion.append(EVENT);
        assertThat(first.sequence()).isEqualTo(1);
        assertThat(first.previousHash()).isEqualTo("0".repeat(64));
        assertThat(first.timestamp()).isEqualTo(first.ingestedAt());
        assertThat(first.ingestedAt().getNano() % 1000).isZero();
        assertThat(second.sequence()).isEqualTo(2);
        assertThat(second.previousHash()).isEqualTo(first.recordHash());
        assertThat(jdbc.queryForObject("SELECT last_hash FROM chain_head WHERE chain_id = 1", String.class))
                .isEqualTo(second.recordHash());
        byte[] stored = jdbc.queryForObject("SELECT canonical_bytes FROM audit_event WHERE event_sequence = 1", byte[].class);
        assertThat(canonical.hash(stored)).isEqualTo(first.recordHash());
        assertThat(new String(stored, StandardCharsets.UTF_8)).doesNotContain("synthetic-secret")
                .contains("$auditProtected", "\"schemaVersion\":1", "\"sequence\":1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payload_key", Integer.class)).isEqualTo(2);
    }

    @Test
    void retainsCallerOccurrenceTimeSeparatelyFromIngestionTime() {
        String request = new String(EVENT, StandardCharsets.UTF_8).replace("\"payload\":",
                "\"timestamp\":\"2020-01-01T00:00:00.123456Z\",\"payload\":");
        AppendReceipt receipt = ingestion.append(request.getBytes(StandardCharsets.UTF_8));
        assertThat(receipt.timestamp()).isEqualTo(java.time.Instant.parse("2020-01-01T00:00:00.123456Z"));
        assertThat(receipt.ingestedAt()).isAfter(receipt.timestamp());
        assertThat(jdbc.queryForObject("SELECT occurred_at FROM audit_event", java.time.OffsetDateTime.class).toInstant())
                .isEqualTo(receipt.timestamp());
    }

    @Test
    void concurrentAppendsHaveNoGapsOrForks() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<AppendReceipt>> tasks = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                tasks.add(() -> ingestion.append(EVENT));
            }
            var futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            HashSet<Long> sequences = new HashSet<>();
            for (var future : futures) {
                sequences.add(future.get().sequence());
            }
            assertThat(sequences).hasSize(32).contains(1L, 32L);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT event_sequence, previous_hash, record_hash, canonical_bytes FROM audit_event ORDER BY event_sequence");
        String previous = "0".repeat(64);
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            assertThat(((Number) row.get("event_sequence")).longValue()).isEqualTo(index + 1L);
            assertThat(row.get("previous_hash")).isEqualTo(previous);
            assertThat(canonical.hash((byte[]) row.get("canonical_bytes"))).isEqualTo(row.get("record_hash"));
            previous = (String) row.get("record_hash");
        }
        assertThat(jdbc.queryForObject("SELECT last_hash FROM chain_head WHERE chain_id = 1", String.class)).isEqualTo(previous);
    }

    @Test
    void failureAfterHeadAdvanceRollsBackEventKeysAndHead() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            ingestion.append(EVENT);
            throw new IllegalStateException("Synthetic failure after append");
        })).isInstanceOf(IllegalStateException.class);
        assertEmpty();
        assertThat(ingestion.append(EVENT).sequence()).isEqualTo(1);
    }

    @Test
    void keyInsertFailureRollsBackPreviouslyInsertedEvent() {
        jdbc.execute("ALTER TABLE payload_key ADD CONSTRAINT test_reject_key CHECK (field_path <> '/accountNumber')");
        try {
            assertThatThrownBy(() -> ingestion.append(EVENT)).isInstanceOf(DataIntegrityViolationException.class);
            assertEmpty();
        } finally {
            jdbc.execute("ALTER TABLE payload_key DROP CONSTRAINT test_reject_key");
        }
        assertThat(ingestion.append(EVENT).sequence()).isEqualTo(1);
    }

    @Test
    void invalidInputNeverWritesOrAdvancesHead() {
        assertThatThrownBy(() -> ingestion.append("{}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(com.example.auditlog.audit.InvalidEventException.class);
        assertEmpty();
    }

    @Test
    void mvcContractCreatesReceiptsRejectsInvalidAndOversizedBodiesAndHasNoMutationRoute() throws Exception {
        // Standalone adapter contract, deliberately no application SecurityFilterChain bypass/profile.
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuditWriteController(ingestion))
                .setControllerAdvice(new EventErrorHandler())
                .defaultRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/")
                        .principal(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("synthetic").header("alg", "RS256")
                                        .subject("contract-operator").claim("audit_global", true).build(), java.util.List.of())))
                .build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/audit/events")
                        .contentType("application/json").content(EVENT))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Location", org.hamcrest.Matchers.startsWith("/audit/events/")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.sequence").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/audit/events")
                        .contentType("application/json").content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/audit/events")
                        .contentType("application/json").content(new byte[256 * 1024 + 1]))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isPayloadTooLarge());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/audit/events"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isMethodNotAllowed());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/audit/events"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isMethodNotAllowed());
    }

    private void assertEmpty() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payload_key", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_sequence FROM chain_head WHERE chain_id = 1", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_hash FROM chain_head WHERE chain_id = 1", String.class))
                .isEqualTo("0".repeat(64));
    }
}
