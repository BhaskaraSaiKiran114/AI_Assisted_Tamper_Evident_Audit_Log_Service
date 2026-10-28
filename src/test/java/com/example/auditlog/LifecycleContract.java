package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.auditlog.audit.AppendReceipt;
import com.example.auditlog.audit.AuditQueryException;
import com.example.auditlog.audit.AuditQueryService;
import com.example.auditlog.audit.ChainVerificationService;
import com.example.auditlog.audit.EventInput;
import com.example.auditlog.audit.EventQuery;
import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.audit.PayloadProtector;
import com.example.auditlog.audit.VerificationResult;
import com.example.auditlog.lifecycle.LifecycleReceipt;
import com.example.auditlog.lifecycle.LifecycleRequest.Redaction;
import com.example.auditlog.lifecycle.LifecycleRequest.Retention;
import com.example.auditlog.lifecycle.LifecycleService;
import com.example.auditlog.persistence.AuditAppender;
import com.example.auditlog.persistence.AuditReadRepository;
import com.example.auditlog.persistence.LifecycleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

abstract class LifecycleContract {

    @Autowired
    private LifecycleService lifecycle;
    @Autowired
    private AuditAppender appender;
    @Autowired
    private PayloadProtector protector;
    @Autowired
    private AuditQueryService queries;
    @Autowired
    private ChainVerificationService verification;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private LifecycleRepository repository;
    @MockitoSpyBean
    private AuditReadRepository reads;

    @BeforeEach
    void clearDisposableStore() {
        reset(repository, reads);
        jdbc.update("DELETE FROM payload_redaction");
        jdbc.update("DELETE FROM event_archive");
        jdbc.update("DELETE FROM lifecycle_action");
        jdbc.update("DELETE FROM payload_key");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("UPDATE chain_head SET last_sequence = 0, last_hash = ?", "0".repeat(64));
    }

    @Test
    void retentionUsesStrictIngestionCutoffBatchesAndPreservesImmutableChainMaterial() {
        Instant cutoff = LifecycleTestClock.NOW.minus(Duration.ofDays(1));
        var old1 = seed(cutoff.minusSeconds(1));
        var old2 = seed(cutoff.minusSeconds(1));
        var old3 = seed(cutoff.minusSeconds(1));
        var boundary = seed(cutoff);
        seed(cutoff.plusNanos(1000));
        seed(LifecycleTestClock.NOW); // Caller occurrence time is old for every fixture, not retention authority.
        byte[] original = bytes(old1.eventId());
        var receipt = lifecycle.retain(new Retention(UUID.randomUUID(), "synthetic-policy-run"), "operator");
        assertThat(receipt.cutoff()).isEqualTo(cutoff);
        assertThat(receipt.affectedEventIds()).containsExactly(old1.eventId(), old2.eventId());
        assertThat(receipt.affectedCount()).isEqualTo(2);
        assertThat(receipt.hasMore()).isTrue();
        assertThat(bytes(old1.eventId())).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT record_hash FROM audit_event WHERE event_id = ?", String.class, old1.eventId()))
                .isEqualTo(old1.recordHash());
        assertThat(queries.query(business(false)).events()).hasSize(4);
        assertThat(queries.query(business(true)).events()).hasSize(6);
        assertThat(queries.find(old1.eventId(), true).archived()).isTrue();
        assertThat(queries.find(boundary.eventId()).archived()).isFalse();
        assertThatThrownBy(() -> queries.find(old1.eventId())).isInstanceOfSatisfying(AuditQueryException.class,
                exception -> assertThat(exception.status()).isEqualTo(404));
        assertThat(verification.verify().intact()).isTrue();
        var next = lifecycle.retain(new Retention(UUID.randomUUID(), "next-batch"), "operator");
        assertThat(next.affectedEventIds()).containsExactly(old3.eventId());
        assertThat(next.hasMore()).isFalse();
        assertThat(verification.verify().intact()).isTrue();
    }

    @Test
    void identicalOperationReplaysButChangedIntentOrOperatorConflicts() {
        seed(LifecycleTestClock.NOW.minus(Duration.ofDays(2)));
        var request = new Retention(UUID.randomUUID(), "reason");
        var first = lifecycle.retain(request, "operator");
        assertThat(lifecycle.retain(request, "operator")).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> lifecycle.retain(new Retention(request.operationId(), "different"), "operator"))
                .isInstanceOfSatisfying(AuditQueryException.class, error -> assertThat(error.status()).isEqualTo(409));
        assertThatThrownBy(() -> lifecycle.retain(request, "another-operator")).isInstanceOf(AuditQueryException.class);
    }

    @Test
    void concurrentDuplicateOperationsProduceOnlyOneAdministrativeEvent() throws Exception {
        seed(LifecycleTestClock.NOW.minus(Duration.ofDays(2)));
        var request = new Retention(UUID.randomUUID(), "reason");
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Callable<LifecycleReceipt>> calls = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> (Callable<LifecycleReceipt>) () -> lifecycle.retain(request, "operator")).toList();
            var results = executor.invokeAll(calls, 20, TimeUnit.SECONDS);
            var expected = results.getFirst().get();
            for (var result : results) {
                assertThat(result.get()).isEqualTo(expected);
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_action", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(2);
        assertThat(verification.verify().intact()).isTrue();
    }

    @Test
    void selectiveErasureLeavesCiphertextHashesAndOtherFieldsUnchanged() {
        var event = seed(LifecycleTestClock.NOW);
        byte[] original = bytes(event.eventId());
        var request = redactRequest(UUID.randomUUID(), List.of("/accountNumber"));
        var receipt = lifecycle.redact(event.eventId(), request, "privacy-operator");
        assertThat(receipt.affectedCount()).isEqualTo(1);
        assertThat(bytes(event.eventId())).isEqualTo(original);
        assertThat(reads.wrappedKeys(event.eventId())).containsOnlyKeys("/personalIdentifier");
        var view = queries.find(event.eventId());
        assertThat(view.redactedPaths()).containsExactly("/accountNumber");
        var payload = (Map<?, ?>) view.payload();
        assertThat(payload.get("personalIdentifier")).isEqualTo("synthetic-person");
        var marker = (Map<?, ?>) ((Map<?, ?>) payload.get("accountNumber")).get("$auditRedacted");
        assertThat(marker.get("operationId")).isEqualTo(request.operationId().toString());
        assertThat(verification.verify().intact()).isTrue();
        assertThat(lifecycle.redact(event.eventId(), request, "privacy-operator")).isEqualTo(receipt);
        var noOp = lifecycle.redact(event.eventId(), redactRequest(UUID.randomUUID(), List.of("/accountNumber")), "privacy-operator");
        assertThat(noOp.affectedCount()).isZero();
        assertThat(verification.verify().intact()).isTrue();
    }

    @Test
    void invalidOrUnprotectedPathCannotPartiallyEraseARequest() {
        var event = seed(LifecycleTestClock.NOW);
        assertThatThrownBy(() -> lifecycle.redact(event.eventId(),
                redactRequest(UUID.randomUUID(), List.of("/accountNumber", "/ordinary")), "operator"))
                .isInstanceOf(InvalidEventException.class);
        assertThat(reads.wrappedKeys(event.eventId())).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_action", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
    }

    @Test
    void unexplainedMissingKeyCannotBeLaunderedIntoAuthorizedRedaction() {
        var event = seed(LifecycleTestClock.NOW);
        jdbc.update("DELETE FROM payload_key WHERE event_id = ? AND field_path = '/accountNumber'", event.eventId());
        assertThatThrownBy(() -> lifecycle.redact(event.eventId(),
                redactRequest(UUID.randomUUID(), List.of("/accountNumber")), "operator"))
                .isInstanceOfSatisfying(AuditQueryException.class, error -> assertThat(error.status()).isEqualTo(503));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_action", Integer.class)).isZero();
        assertThat(verification.verify().intact()).isTrue();
    }

    @Test
    void failureAfterActualKeyDeletionRestoresKeyMetadataAuditEventAndHead() {
        var event = seed(LifecycleTestClock.NOW);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Synthetic failure after key deletion");
        }).when(repository).eraseKey(any(), anyString(), any());
        assertThatThrownBy(() -> lifecycle.redact(event.eventId(),
                redactRequest(UUID.randomUUID(), List.of("/accountNumber")), "operator"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reads.wrappedKeys(event.eventId())).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payload_redaction", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_action", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_sequence FROM chain_head", Long.class)).isEqualTo(1);
        assertThat(((Map<?, ?>) queries.find(event.eventId()).payload()).get("accountNumber")).isEqualTo("synthetic-account");
        assertThat(verification.verify().intact()).isTrue();
    }

    @Test
    void failureAfterArchiveInsertRollsBackTheEntireOperation() {
        var event = seed(LifecycleTestClock.NOW.minus(Duration.ofDays(2)));
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Synthetic failure after archive insertion");
        }).when(repository).archive(any(), any(), any());
        assertThatThrownBy(() -> lifecycle.retain(new Retention(UUID.randomUUID(), "reason"), "operator"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_archive", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_action", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
        assertThat(queries.find(event.eventId()).archived()).isFalse();
        assertThat(verification.verify().intact()).isTrue();
    }

    @Test
    void archiveMetadataTamperingIsDetectedAtItsAdministrativeRecord() {
        seed(LifecycleTestClock.NOW.minus(Duration.ofDays(2)));
        var receipt = lifecycle.retain(new Retention(UUID.randomUUID(), "reason"), "operator");
        jdbc.update("UPDATE event_archive SET archived_at = ?", java.time.OffsetDateTime.parse("2001-01-01T00:00:00Z"));
        assertLifecycleViolation(receipt.auditEventId());
    }

    @Test
    void deletingRedactionMetadataOrRestoringAKeyCannotRevealRedactedData() {
        var event = seed(LifecycleTestClock.NOW);
        String key = reads.wrappedKeys(event.eventId()).get("/accountNumber");
        var receipt = lifecycle.redact(event.eventId(), redactRequest(UUID.randomUUID(), List.of("/accountNumber")), "operator");
        jdbc.update("INSERT INTO payload_key (event_id, field_path, key_version, wrapped_key) VALUES (?, ?, 1, ?)",
                event.eventId(), "/accountNumber", key);
        assertLifecycleViolation(receipt.auditEventId());
        assertThatThrownBy(() -> queries.find(event.eventId())).isInstanceOf(AuditQueryException.class);
        jdbc.update("DELETE FROM payload_key WHERE event_id = ? AND field_path = '/accountNumber'", event.eventId());
        jdbc.update("DELETE FROM payload_redaction");
        assertLifecycleViolation(receipt.auditEventId());
        assertThatThrownBy(() -> queries.find(event.eventId())).isInstanceOf(AuditQueryException.class);
    }

    @Test
    void removingActionProofDoesNotLeaveAnApparentlyValidAdministrativeEvent() {
        seed(LifecycleTestClock.NOW.minus(Duration.ofDays(2)));
        var receipt = lifecycle.retain(new Retention(UUID.randomUUID(), "reason"), "operator");
        jdbc.update("DELETE FROM event_archive");
        jdbc.update("DELETE FROM lifecycle_action");
        assertLifecycleViolation(receipt.auditEventId());
    }

    @Test
    void archivedRecordsCanBeRedactedWithoutBreakingEitherEvidenceTrail() {
        var event = seed(LifecycleTestClock.NOW.minus(Duration.ofDays(2)));
        lifecycle.retain(new Retention(UUID.randomUUID(), "reason"), "operator");
        lifecycle.redact(event.eventId(), redactRequest(UUID.randomUUID(), List.of("/accountNumber", "/personalIdentifier")), "operator");
        var view = queries.find(event.eventId(), true);
        assertThat(view.archived()).isTrue();
        assertThat(view.redactedPaths()).containsExactly("/accountNumber", "/personalIdentifier");
        assertThat(reads.wrappedKeys(event.eventId())).isEmpty();
        assertThat(verification.verify().intact()).isTrue();
    }

    @Test
    void verificationSnapshotRemainsConsistentDuringCommittedKeyErasure() throws Exception {
        var event = seed(LifecycleTestClock.NOW);
        CountDownLatch headRead = new CountDownLatch(1);
        CountDownLatch committed = new CountDownLatch(1);
        doAnswer(invocation -> {
            headRead.countDown();
            if (!committed.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Synthetic lifecycle writer did not finish");
            }
            return invocation.callRealMethod();
        }).when(reads).streamAll();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var before = executor.submit(() -> verification.verify());
            try {
                assertThat(headRead.await(10, TimeUnit.SECONDS)).isTrue();
                lifecycle.redact(event.eventId(), redactRequest(UUID.randomUUID(), List.of("/accountNumber")), "operator");
            } finally {
                committed.countDown();
            }
            assertThat(before.get(10, TimeUnit.SECONDS).intact()).isTrue();
        } finally {
            committed.countDown();
            reset(reads);
        }
        assertThat(verification.verify().intact()).isTrue();
        assertThat(verification.verify().checkedCount()).isEqualTo(2);
    }

    private AppendReceipt seed(Instant ingested) {
        UUID id = UUID.randomUUID();
        Map<String, Object> payload = Map.of("accountNumber", "synthetic-account", "personalIdentifier", "synthetic-person", "ordinary", "visible");
        return appender.append(id, ingested, new EventInput("READ", "actor", "ACCOUNT", "resource", payload,
                Instant.parse("2000-01-01T00:00:00Z")), protector.protect(id, payload));
    }

    private Redaction redactRequest(UUID id, List<String> paths) {
        return new Redaction(id, "synthetic-reason", "synthetic-approved-change", paths);
    }

    private byte[] bytes(UUID id) {
        return jdbc.queryForObject("SELECT canonical_bytes FROM audit_event WHERE event_id = ?", byte[].class, id);
    }

    private EventQuery business(boolean archived) {
        return new EventQuery(null, null, null, "READ", null, null, 50, null, archived);
    }

    private void assertLifecycleViolation(UUID auditId) {
        var result = verification.verify();
        assertThat(result.intact()).isFalse();
        assertThat(result.inconsistency().type()).isEqualTo(VerificationResult.ViolationType.LIFECYCLE_MISMATCH);
        assertThat(result.inconsistency().eventId()).isEqualTo(auditId);
    }
}
