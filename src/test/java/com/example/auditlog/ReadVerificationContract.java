package com.example.auditlog;

import static com.example.auditlog.audit.VerificationResult.ViolationType.CONTENT_HASH_MISMATCH;
import static com.example.auditlog.audit.VerificationResult.ViolationType.HEAD_MISMATCH;
import static com.example.auditlog.audit.VerificationResult.ViolationType.INDEX_FIELD_MISMATCH;
import static com.example.auditlog.audit.VerificationResult.ViolationType.MALFORMED_CONTENT;
import static com.example.auditlog.audit.VerificationResult.ViolationType.PREVIOUS_HASH_MISMATCH;
import static com.example.auditlog.audit.VerificationResult.ViolationType.SEQUENCE_GAP;
import static com.example.auditlog.audit.VerificationResult.ViolationType.UNSUPPORTED_SCHEMA_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.auditlog.api.AuditReadController;
import com.example.auditlog.api.EventErrorHandler;
import com.example.auditlog.audit.AppendReceipt;
import com.example.auditlog.audit.AuditIngestionService;
import com.example.auditlog.audit.AuditQueryException;
import com.example.auditlog.audit.AuditQueryService;
import com.example.auditlog.audit.CanonicalJson;
import com.example.auditlog.audit.ChainVerificationService;
import com.example.auditlog.audit.EventQuery;
import com.example.auditlog.audit.StoredJson;
import com.example.auditlog.audit.VerificationResult;
import com.example.auditlog.audit.VerificationResult.ViolationType;
import com.example.auditlog.persistence.AuditReadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

abstract class ReadVerificationContract {

    @Autowired
    private AuditIngestionService ingestion;
    @Autowired
    private AuditQueryService queries;
    @Autowired
    private ChainVerificationService verification;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CanonicalJson canonical;
    @Autowired
    private StoredJson json;
    @MockitoSpyBean
    private AuditReadRepository repository;

    @BeforeEach
    void resetDisposableStore() {
        reset(repository);
        jdbc.update("DELETE FROM payload_redaction");
        jdbc.update("DELETE FROM event_archive");
        jdbc.update("DELETE FROM lifecycle_action");
        jdbc.update("DELETE FROM payload_key");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM chain_head");
        jdbc.update("INSERT INTO chain_head (chain_id, last_sequence, last_hash) VALUES (1, 0, ?)", "0".repeat(64));
    }

    @Test
    void emptyAndNonemptyChainsVerifyAndLookupRestoresOriginalPayload() {
        assertThat(verification.verify()).isEqualTo(new VerificationResult(true, 0,
                new VerificationResult.ChainHead(0, "0".repeat(64)), null));
        assertThat(queries.query(EventQuery.parse(Map.of())).events()).isEmpty();
        var first = append("alice", "ACCOUNT", "a1", "READ", "2020-01-01T00:00:00Z");
        append("bob", "DOCUMENT", "d1", "WRITE", "2020-01-02T00:00:00Z");
        var result = verification.verify();
        assertThat(result.intact()).isTrue();
        assertThat(result.checkedCount()).isEqualTo(2);
        assertThat(result.inconsistency()).isNull();
        var view = queries.find(first.eventId());
        assertThat(view.actorId()).isEqualTo("alice");
        assertThat(((Map<?, ?>) view.payload()).get("accountNumber")).isEqualTo("synthetic-sensitive");
        assertThatThrownBy(() -> queries.find(UUID.randomUUID())).isInstanceOfSatisfying(AuditQueryException.class,
                failure -> assertThat(failure.status()).isEqualTo(404));
    }

    @Test
    void filtersCombineAndTimeRangeIsInclusiveExclusive() {
        append("alice", "ACCOUNT", "a1", "READ", "2020-01-01T00:00:00Z");
        append("bob", "ACCOUNT", "a1", "READ", "2020-01-02T00:00:00Z");
        append("alice", "DOCUMENT", "a1", "WRITE", "2020-01-03T00:00:00Z");
        append("alice", "ACCOUNT", "a2", "READ", "2020-01-04T00:00:00Z");
        assertSequences(query("alice", null, null, null, null, null, 50, null), 1L, 3L, 4L);
        assertSequences(query(null, "ACCOUNT", null, null, null, null, 50, null), 1L, 2L, 4L);
        assertSequences(query(null, null, "a1", null, null, null, 50, null), 1L, 2L, 3L);
        assertSequences(query(null, null, null, "WRITE", null, null, 50, null), 3L);
        assertSequences(query("alice", "ACCOUNT", "a1", "READ", "2020-01-01T00:00:00Z",
                "2020-01-04T00:00:00Z", 50, null), 1L);
        assertSequences(query(null, null, null, null, "2020-01-02T00:00:00Z", "2020-01-04T00:00:00Z", 50, null), 2L, 3L);
        assertSequences(query(null, null, null, null, null, "2020-01-02T00:00:00Z", 50, null), 1L);
        assertSequences(query(null, null, null, null, "2020-01-04T00:00:00Z", null, 50, null), 4L);
        assertSequences(query("alice' OR 1=1 --", null, null, null, null, null, 50, null));
    }

    @Test
    void paginationHasNoDuplicatesOrLaterAppendsAndBindsFilters() {
        for (int index = 0; index < 5; index++) {
            append("alice", "ACCOUNT", "a1", "READ", "2020-01-01T00:00:00Z");
            append("bob", "ACCOUNT", "a1", "READ", "2020-01-01T00:00:00Z");
        }
        var first = queries.query(query("alice", null, null, null, null, null, 2, null));
        append("alice", "ACCOUNT", "a1", "READ", "2020-01-01T00:00:00Z");
        var second = queries.query(query("alice", null, null, null, null, null, 2, first.nextCursor()));
        var third = queries.query(query("alice", null, null, null, null, null, 2, second.nextCursor()));
        List<Long> sequences = new ArrayList<>();
        for (var page : List.of(first, second, third)) {
            sequences.addAll(page.events().stream().map(AuditQueryService.EventView::sequence).toList());
            assertThat(page.snapshotHead()).isEqualTo(first.snapshotHead());
        }
        assertThat(sequences).containsExactly(1L, 3L, 5L, 7L, 9L);
        assertThat(third.nextCursor()).isNull();
        assertThatThrownBy(() -> queries.query(query("bob", null, null, null, null, null, 2, first.nextCursor())))
                .isInstanceOf(com.example.auditlog.audit.InvalidEventException.class);
    }

    @Test
    void changedSnapshotAnchorRejectsContinuation() {
        seed(3);
        var first = queries.query(query(null, null, null, null, null, null, 1, null));
        jdbc.update("UPDATE audit_event SET record_hash = ? WHERE event_sequence = 3", "f".repeat(64));
        assertThatThrownBy(() -> queries.query(query(null, null, null, null, null, null, 1, first.nextCursor())))
                .isInstanceOfSatisfying(AuditQueryException.class, failure -> assertThat(failure.status()).isEqualTo(409));
    }

    @Test
    void changedCanonicalContentReportsFirstBadRecord() {
        seed(3);
        var id = idAt(2);
        jdbc.update("UPDATE audit_event SET canonical_bytes = ? WHERE event_sequence = 2", "{}".getBytes(StandardCharsets.UTF_8));
        var result = assertBroken(2, CONTENT_HASH_MISMATCH);
        assertThat(result.inconsistency().eventId()).isEqualTo(id);
        assertThat(result.checkedCount()).isEqualTo(2);
        assertThatThrownBy(() -> queries.find(id)).isInstanceOfSatisfying(AuditQueryException.class,
                failure -> assertThat(failure.status()).isEqualTo(503));
    }

    @Test
    void eachSearchColumnAndStoredPayloadMustAgreeWithCommittedBytes() {
        seed(1);
        for (String column : List.of("actor_id", "event_type", "resource_type", "resource_id", "previous_hash")) {
            String original = jdbc.queryForObject("SELECT " + column + " FROM audit_event", String.class);
            jdbc.update("UPDATE audit_event SET " + column + " = ?", "b".repeat(64));
            assertBroken(1, INDEX_FIELD_MISMATCH);
            jdbc.update("UPDATE audit_event SET " + column + " = ?", original);
        }
        jdbc.update("UPDATE audit_event SET payload_json = ?", "{\"changed\":true}");
        assertBroken(1, INDEX_FIELD_MISMATCH);
    }

    @Test
    void timestampColumnChangeCannotEvadeVerification() {
        seed(1);
        jdbc.update("UPDATE audit_event SET occurred_at = ?", java.time.OffsetDateTime.parse("2021-01-01T00:00:00Z"));
        assertBroken(1, INDEX_FIELD_MISMATCH);
    }

    @Test
    void recomputedOwnHashDoesNotRepairBrokenPredecessor() {
        seed(3);
        Map<String, Object> envelope = json.object(jdbc.queryForObject(
                "SELECT canonical_bytes FROM audit_event WHERE event_sequence = 2", byte[].class));
        envelope.put("previousHash", "d".repeat(64));
        byte[] changed = canonical.bytes(envelope);
        jdbc.update("UPDATE audit_event SET previous_hash = ?, canonical_bytes = ?, record_hash = ? WHERE event_sequence = 2",
                "d".repeat(64), changed, canonical.hash(changed));
        assertBroken(2, PREVIOUS_HASH_MISMATCH);
    }

    @Test
    void changingContentAndRecomputingItsOwnHashStillBreaksTheFollowingRecord() {
        seed(3);
        Map<String, Object> envelope = json.object(jdbc.queryForObject(
                "SELECT canonical_bytes FROM audit_event WHERE event_sequence = 1", byte[].class));
        envelope.put("actorId", "changed");
        byte[] changed = canonical.bytes(envelope);
        jdbc.update("UPDATE audit_event SET actor_id = ?, canonical_bytes = ?, record_hash = ? WHERE event_sequence = 1",
                "changed", changed, canonical.hash(changed));
        assertBroken(2, PREVIOUS_HASH_MISMATCH);
    }

    @Test
    void deletedInternalOrTailRecordReportsMissingSequenceWithoutInventedEventId() {
        seed(3);
        deleteAt(2);
        var gap = assertBroken(2, SEQUENCE_GAP);
        assertThat(gap.inconsistency().eventId()).isNull();
        resetDisposableStore();
        seed(3);
        deleteAt(3);
        assertBroken(3, SEQUENCE_GAP);
    }

    @Test
    void corruptOrMissingHeadAndUnderreportedHeadAreDetected() {
        seed(2);
        jdbc.update("UPDATE chain_head SET last_hash = ?", "e".repeat(64));
        assertBroken(2, HEAD_MISMATCH);
        jdbc.update("UPDATE chain_head SET last_sequence = 0, last_hash = ?", "0".repeat(64));
        assertBroken(1, HEAD_MISMATCH);
        jdbc.update("DELETE FROM chain_head");
        var missing = assertBroken(0, HEAD_MISMATCH);
        assertThat(missing.snapshotHead()).isNull();
    }

    @Test
    void malformedButRehashedCanonicalDataIsAnIntegrityViolation() {
        seed(1);
        byte[] invalid = "not-json".getBytes(StandardCharsets.UTF_8);
        jdbc.update("UPDATE audit_event SET canonical_bytes = ?, record_hash = ?", invalid, canonical.hash(invalid));
        assertBroken(1, MALFORMED_CONTENT);
    }

    @Test
    void unknownCommittedSchemaVersionCannotBeReportedIntact() {
        seed(1);
        Map<String, Object> envelope = json.object(jdbc.queryForObject("SELECT canonical_bytes FROM audit_event", byte[].class));
        envelope.put("schemaVersion", 2);
        byte[] changed = canonical.bytes(envelope);
        jdbc.update("UPDATE audit_event SET canonical_bytes = ?, record_hash = ?", changed, canonical.hash(changed));
        assertBroken(1, UNSUPPORTED_SCHEMA_VERSION);
    }

    @Test
    void keyAvailabilityIsSeparateFromChainIntegrityAndIsNotFalselyCalledRedaction() {
        var receipt = append("alice", "ACCOUNT", "a1", "READ", "2020-01-01T00:00:00Z");
        jdbc.update("DELETE FROM payload_key");
        assertThat(verification.verify().intact()).isTrue();
        assertThatThrownBy(() -> queries.find(receipt.eventId())).isInstanceOfSatisfying(AuditQueryException.class,
                failure -> assertThat(failure.status()).isEqualTo(503));
    }

    @Test
    void verificationUsesOneSnapshotWhileAnAppendCommitsAfterHeadRead() throws Exception {
        seed(1);
        CountDownLatch headRead = new CountDownLatch(1);
        CountDownLatch appended = new CountDownLatch(1);
        doAnswer(invocation -> {
            headRead.countDown();
            if (!appended.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Writer did not complete within snapshot test");
            }
            return invocation.callRealMethod();
        }).when(repository).streamAll();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var result = executor.submit(() -> verification.verify());
            try {
                assertThat(headRead.await(10, TimeUnit.SECONDS)).isTrue();
                append("writer", "ACCOUNT", "a1", "READ", "2020-01-02T00:00:00Z");
            } finally {
                appended.countDown();
            }
            var snapshot = result.get(15, TimeUnit.SECONDS);
            assertThat(snapshot.intact()).isTrue();
            assertThat(snapshot.checkedCount()).isEqualTo(1);
            assertThat(snapshot.snapshotHead().sequence()).isEqualTo(1);
        } finally {
            reset(repository);
        }
        assertThat(verification.verify().checkedCount()).isEqualTo(2);
    }

    @Test
    void standaloneApiReturnsVerificationResultsAndSafeOperationalErrors() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuditReadController(queries, verification))
                .setControllerAdvice(new EventErrorHandler())
                .defaultRequest(get("/").principal(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("synthetic").header("alg", "RS256")
                                .subject("contract-operator").claim("audit_global", true).build(), java.util.List.of())))
                .build();
        var receipt = append("alice", "ACCOUNT", "a1", "READ", "2020-01-01T00:00:00Z");
        mvc.perform(get("/audit/events").param("actorId", "alice"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events[0].eventId").value(receipt.eventId().toString()));
        mvc.perform(get("/audit/events/" + receipt.eventId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.accountNumber").value("synthetic-sensitive"));
        mvc.perform(get("/audit/events/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/audit/events/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/audit/events").param("limit", "201")).andExpect(status().isBadRequest());
        mvc.perform(get("/audit/events").param("actorId", "a", "b")).andExpect(status().isBadRequest());
        mvc.perform(get("/audit/verify").param("actorId", "alice")).andExpect(status().isBadRequest());
        mvc.perform(get("/audit/verify")).andExpect(status().isOk()).andExpect(jsonPath("$.intact").value(true));
        jdbc.update("UPDATE audit_event SET actor_id = 'changed'");
        mvc.perform(get("/audit/verify")).andExpect(status().isOk()).andExpect(jsonPath("$.intact").value(false))
                .andExpect(jsonPath("$.inconsistency.type").value("INDEX_FIELD_MISMATCH"));
        doThrow(new DataAccessResourceFailureException("Synthetic private storage details")).when(repository).head();
        mvc.perform(get("/audit/verify")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Audit storage temporarily unavailable"))
                .andExpect(jsonPath("$.intact").doesNotExist());
    }

    private AppendReceipt append(String actor, String resourceType, String resource, String type, String timestamp) {
        return ingestion.append(canonical.bytes(Map.of("actorId", actor, "resourceType", resourceType,
                "resourceId", resource, "eventType", type, "timestamp", timestamp,
                "payload", Map.of("accountNumber", "synthetic-sensitive", "value", 1))));
    }

    private void seed(int count) {
        for (int index = 0; index < count; index++) {
            append("alice", "ACCOUNT", "a1", "READ", "2020-01-01T00:00:00Z");
        }
    }

    private UUID idAt(long sequence) {
        return jdbc.queryForObject("SELECT event_id FROM audit_event WHERE event_sequence = ?", UUID.class, sequence);
    }

    private void deleteAt(long sequence) {
        UUID id = idAt(sequence);
        jdbc.update("DELETE FROM payload_key WHERE event_id = ?", id);
        jdbc.update("DELETE FROM audit_event WHERE event_id = ?", id);
    }

    private VerificationResult assertBroken(long sequence, ViolationType type) {
        var result = verification.verify();
        assertThat(result.intact()).isFalse();
        assertThat(result.inconsistency().sequence()).isEqualTo(sequence);
        assertThat(result.inconsistency().type()).isEqualTo(type);
        return result;
    }

    private EventQuery query(String actor, String resourceType, String resource, String type,
            String from, String to, int limit, String cursor) {
        return new EventQuery(actor, resourceType, resource, type, from == null ? null : Instant.parse(from),
                to == null ? null : Instant.parse(to), limit, cursor);
    }

    private void assertSequences(EventQuery query, Long... sequences) {
        assertThat(queries.query(query).events().stream().map(AuditQueryService.EventView::sequence).toList())
                .containsExactly(sequences);
    }
}
