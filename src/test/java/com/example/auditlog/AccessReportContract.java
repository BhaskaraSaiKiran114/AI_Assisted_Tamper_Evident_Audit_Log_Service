package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.auditlog.audit.AuditQueryException;
import com.example.auditlog.audit.CanonicalJson;
import com.example.auditlog.audit.EventInput;
import com.example.auditlog.audit.EventQuery;
import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.audit.PayloadProtector;
import com.example.auditlog.audit.QueryCursorCodec;
import com.example.auditlog.audit.QueryCursorCodec.Position;
import com.example.auditlog.lifecycle.LifecycleRequest.Redaction;
import com.example.auditlog.lifecycle.LifecycleRequest.Retention;
import com.example.auditlog.lifecycle.LifecycleService;
import com.example.auditlog.persistence.AuditAppender;
import com.example.auditlog.persistence.AuditReadRepository;
import com.example.auditlog.report.AccessReportQuery;
import com.example.auditlog.report.AccessReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

abstract class AccessReportContract {

    static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    static final Instant TO = FROM.plusSeconds(86400);
    @Autowired
    private AccessReportService reports;
    @Autowired
    private AuditAppender appender;
    @Autowired
    private PayloadProtector protector;
    @Autowired
    private LifecycleService lifecycle;
    @Autowired
    private QueryCursorCodec cursors;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private AuditReadRepository reads;

    @BeforeEach
    void clearFixture() {
        reset(reads);
        jdbc.update("DELETE FROM payload_redaction");
        jdbc.update("DELETE FROM event_archive");
        jdbc.update("DELETE FROM lifecycle_action");
        jdbc.update("DELETE FROM payload_key");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("UPDATE chain_head SET last_sequence = 0, last_hash = ?", "0".repeat(64));
    }

    @Test
    void allRecognizedActionsAndOutcomesUseOccurrenceRangeWithExclusiveUpperBound() {
        UUID first = seed("a", "one", "READ", "SUCCESS", FROM);
        UUID second = seed("b", "two", "UPDATE", "DENIED", FROM.plusSeconds(1));
        UUID third = seed("c", "three", "EXPORT", "FAILURE", TO.minusNanos(1000));
        seed("a", "one", "READ", "SUCCESS", FROM.minusNanos(1000));
        seed("a", "one", "READ", "SUCCESS", TO);
        var result = reports.report(query(null, null, null, null, 50, null, false));
        assertThat(result.entries()).extracting(AccessReportService.Entry::eventId).containsExactly(first, second, third);
        assertThat(result.entries()).extracting(AccessReportService.Entry::action).containsExactly("READ", "UPDATE", "EXPORT");
        assertThat(result.entries()).extracting(AccessReportService.Entry::outcome).containsExactly("SUCCESS", "DENIED", "FAILURE");
        assertThat(result.scannedCount()).isEqualTo(3);
        assertThat(result.reportVersion()).isEqualTo("access-report-v1");
    }

    @Test
    void allFiltersCombineExactlyWithoutSqlWildcardOrInjectionSemantics() {
        String account = "account' OR '1'='1_%";
        UUID target = seed("alice", account, "READ", "SUCCESS", FROM);
        seed("bob", account, "READ", "SUCCESS", FROM);
        seed("alice", "other", "READ", "SUCCESS", FROM);
        seed("alice", account, "UPDATE", "SUCCESS", FROM);
        seed("alice", account, "READ", "DENIED", FROM);
        var result = reports.report(query(account, "alice", "READ", "SUCCESS", 50, null, false));
        assertThat(result.entries()).extracting(AccessReportService.Entry::eventId).containsExactly(target);
        assertThat(result.scannedCount()).isEqualTo(2);
        assertThat(result.excludedOutcomeCount()).isZero();
    }

    @Test
    void unsupportedOutcomeIsCountedAndOtherResourceOrEventTypesAreOutsideDeclaredScope() {
        seed("a", "one", "READ", null, FROM);
        seed("a", "one", "READ", 1, FROM);
        seed("a", "one", "READ", "UNKNOWN", FROM);
        seed("a", "one", "READ", Map.of("nested", "SUCCESS"), FROM);
        UUID target = seed("a", "one", "READ", "SUCCESS", FROM);
        UUID ignored = UUID.randomUUID();
        var payload = Map.<String, Object>of("outcome", "SUCCESS");
        appender.append(ignored, FROM, new EventInput("READ", "a", "ACCOUNT", "one", payload, FROM), protector.protect(ignored, payload));
        seed("a", "one", "LOGIN", "SUCCESS", FROM);
        var result = reports.report(query(null, null, null, null, 50, null, false));
        assertThat(result.entries()).extracting(AccessReportService.Entry::eventId).containsExactly(target);
        assertThat(result.scannedCount()).isEqualTo(5);
        assertThat(result.excludedOutcomeCount()).isEqualTo(4);
    }

    @Test
    void sparseSignedPaginationExcludesLaterAppendsAndDoesNotSkipMatches() {
        List<UUID> expected = new ArrayList<>();
        expected.add(seed("a", "one", "READ", "SUCCESS", FROM));
        seed("a", "one", "READ", "DENIED", FROM);
        seed("a", "one", "READ", null, FROM);
        expected.add(seed("a", "one", "READ", "SUCCESS", FROM));
        seed("a", "one", "READ", "DENIED", FROM);
        expected.add(seed("a", "one", "READ", "SUCCESS", FROM));
        var first = reports.report(query(null, null, null, "SUCCESS", 1, null, false));
        assertThat(first.scannedCount()).isEqualTo(4);
        assertThat(first.excludedOutcomeCount()).isEqualTo(1);
        seed("a", "one", "READ", "SUCCESS", FROM);
        List<UUID> actual = new ArrayList<>();
        actual.add(first.entries().getFirst().eventId());
        String cursor = first.nextCursor();
        while (cursor != null) {
            var page = reports.report(query(null, null, null, "SUCCESS", 1, cursor, false));
            assertThat(page.snapshotHead()).isEqualTo(first.snapshotHead());
            actual.addAll(page.entries().stream().map(AccessReportService.Entry::eventId).toList());
            cursor = page.nextCursor();
        }
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void archiveOptInAndRedactionDoNotRequirePayloadDecryptionOrExposeKeys() {
        UUID id = seed("a", "one", "READ", "SUCCESS", FROM);
        lifecycle.redact(id, new Redaction(UUID.randomUUID(), "synthetic-privacy", "synthetic-approved", List.of("/accountNumber")), "operator");
        lifecycle.retain(new Retention(UUID.randomUUID(), "synthetic-retention"), "operator");
        clearInvocations(reads); // The lifecycle command legitimately checks keys; the report must not.
        assertThat(reports.report(query(null, null, null, null, 50, null, false)).entries()).isEmpty();
        var result = reports.report(query(null, null, null, null, 50, null, true));
        assertThat(result.entries()).hasSize(1);
        assertThat(result.entries().getFirst().archived()).isTrue();
        assertThat(result.entries().getFirst().outcome()).isEqualTo("SUCCESS");
        verify(reads, never()).wrappedKeys(any());
    }

    @Test
    void emptyGenesisReportHasNoCursorOrClaimedAccess() {
        var result = reports.report(query(null, null, null, null, 50, null, false));
        assertThat(result.entries()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.scannedCount()).isZero();
        assertThat(result.excludedOutcomeCount()).isZero();
        assertThat(result.snapshotHead().sequence()).isZero();
        assertThat(result.snapshotHead().hash()).isEqualTo("0".repeat(64));
    }

    @Test
    void protectedOutcomeIsExcludedRatherThanDecryptedAndMissingUnrelatedKeysDoNotBreakReport() {
        UUID id = UUID.randomUUID();
        var payload = Map.<String, Object>of("outcome", "SUCCESS");
        var special = new PayloadProtector(new CanonicalJson(), "", true, "/outcome");
        appender.append(id, FROM, new EventInput("READ", "a", "CLIENT_ACCOUNT", "one", payload, FROM), special.protect(id, payload));
        UUID clear = seed("a", "one", "READ", "SUCCESS", FROM);
        jdbc.update("DELETE FROM payload_key");
        var result = reports.report(query(null, null, null, null, 50, null, false));
        assertThat(result.entries()).extracting(AccessReportService.Entry::eventId).containsExactly(clear);
        assertThat(result.excludedOutcomeCount()).isEqualTo(1);
        verify(reads, never()).wrappedKeys(any());
    }

    @Test
    void scanBudgetFailsWholeRequestAndReleasesPermit() {
        for (int index = 0; index < 6; index++) {
            seed("a", "one", "READ", "UNKNOWN", FROM);
        }
        assertThatThrownBy(() -> reports.report(query(null, null, null, null, 50, null, false)))
                .isInstanceOf(AuditQueryException.class).satisfies(error -> assertThat(((AuditQueryException) error).status()).isEqualTo(413));
        assertThat(reports.report(query("absent", null, null, null, 50, null, false)).entries()).isEmpty();
    }

    @Test
    void corruptedCandidateAndArchiveProofFailClosedAndCapacityRecovers() {
        UUID id = seed("a", "one", "READ", "SUCCESS", FROM);
        String original = jdbc.queryForObject("SELECT payload_json FROM audit_event WHERE event_id = ?", String.class, id);
        jdbc.update("UPDATE audit_event SET payload_json = '{}' WHERE event_id = ?", id);
        assert503(query(null, null, null, null, 50, null, false));
        jdbc.update("UPDATE audit_event SET payload_json = ? WHERE event_id = ?", original, id);
        assertThat(reports.report(query(null, null, null, null, 50, null, false)).entries()).hasSize(1);
        lifecycle.retain(new Retention(UUID.randomUUID(), "synthetic"), "operator");
        jdbc.update("UPDATE event_archive SET archived_at = ?", java.time.OffsetDateTime.parse("2000-01-01T00:00:00Z"));
        assert503(query(null, null, null, null, 50, null, true));
    }

    @Test
    void cursorBindsOutcomeActionAccountActorRangeLimitAndArchiveModeAndRejectsOrdinaryQueryCursor() {
        seed("a", "one", "READ", "SUCCESS", FROM);
        seed("a", "one", "READ", "SUCCESS", FROM);
        String cursor = reports.report(query(null, null, null, null, 1, null, false)).nextCursor();
        for (var changed : List.of(query("one", null, null, null, 1, cursor, false), query(null, "a", null, null, 1, cursor, false),
                query(null, null, "READ", null, 1, cursor, false), query(null, null, null, "SUCCESS", 1, cursor, false),
                query(null, null, null, null, 2, cursor, false), query(null, null, null, null, 1, cursor, true),
                new AccessReportQuery(null, null, null, null, FROM, TO.plusSeconds(1), 1, cursor, false))) {
            assertThatThrownBy(() -> reports.report(changed)).isInstanceOf(InvalidEventException.class);
        }
        String ordinary = cursors.encode(new Position(1, 2, reads.head().orElseThrow().hash()),
                new EventQuery(null, "CLIENT_ACCOUNT", null, null, FROM, TO, 1, null, false));
        assertThatThrownBy(() -> reports.report(query(null, null, null, null, 1, ordinary, false))).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> cursors.decode(cursor, new EventQuery(null, "CLIENT_ACCOUNT", null, null, FROM, TO, 1, null, false)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void unavailableCursorAnchorReturns409AndOversizedTimeWindowIs400() {
        seed("a", "one", "READ", "SUCCESS", FROM);
        UUID last = seed("a", "one", "READ", "SUCCESS", FROM);
        String cursor = reports.report(query(null, null, null, null, 1, null, false)).nextCursor();
        jdbc.update("DELETE FROM payload_key WHERE event_id = ?", last);
        jdbc.update("DELETE FROM audit_event WHERE event_id = ?", last);
        assertThatThrownBy(() -> reports.report(query(null, null, null, null, 1, cursor, false)))
                .isInstanceOf(AuditQueryException.class).satisfies(error -> assertThat(((AuditQueryException) error).status()).isEqualTo(409));
        assertThatThrownBy(() -> reports.report(new AccessReportQuery(null, null, null, null, FROM, FROM.plusSeconds(32 * 86400L),
                50, null, false))).isInstanceOf(InvalidEventException.class);
    }

    @Test
    void concurrentAppendUsesSnapshotAndSaturationReturns429() throws Exception {
        seed("a", "one", "READ", "SUCCESS", FROM);
        CountDownLatch captured = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            Object head = call.callRealMethod();
            captured.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Report test latch timeout");
            }
            return head;
        }).when(reads).head();
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = pool.submit(() -> reports.report(query(null, null, null, null, 50, null, false)));
            try {
                assertThat(captured.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> reports.report(query(null, null, null, null, 50, null, false)))
                        .isInstanceOf(AuditQueryException.class).satisfies(error -> assertThat(((AuditQueryException) error).status()).isEqualTo(429));
                seed("a", "one", "READ", "SUCCESS", FROM);
            } finally {
                release.countDown();
            }
            assertThat(future.get(15, TimeUnit.SECONDS).entries()).hasSize(1);
        } finally {
            reset(reads);
        }
        assertThat(reports.report(query(null, null, null, null, 50, null, false)).entries()).hasSize(2);
    }

    protected UUID seed(String actor, String account, String action, Object outcome, Instant occurred) {
        UUID id = UUID.randomUUID();
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("accountNumber", "synthetic-sensitive");
        payload.put("ordinary", "do-not-expose-this-payload");
        if (outcome != null) {
            payload.put("outcome", outcome);
        }
        appender.append(id, Instant.parse("2020-01-01T00:00:00Z"),
                new EventInput(action, actor, "CLIENT_ACCOUNT", account, payload, occurred), protector.protect(id, payload));
        return id;
    }

    private AccessReportQuery query(String account, String actor, String action, String outcome, int limit, String cursor, boolean archived) {
        return new AccessReportQuery(account, actor, action, outcome, FROM, TO, limit, cursor, archived);
    }

    private void assert503(AccessReportQuery query) {
        assertThatThrownBy(() -> reports.report(query)).isInstanceOf(AuditQueryException.class)
                .satisfies(error -> assertThat(((AuditQueryException) error).status()).isEqualTo(503));
    }
}
