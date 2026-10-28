package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

import com.example.auditlog.audit.AuditQueryException;
import com.example.auditlog.audit.EventInput;
import com.example.auditlog.audit.EventIntegrityException;
import com.example.auditlog.audit.PayloadProtector;
import com.example.auditlog.export.ExportBundle;
import com.example.auditlog.export.ExportFilter;
import com.example.auditlog.export.ExportService;
import com.example.auditlog.lifecycle.LifecycleRequest.Redaction;
import com.example.auditlog.lifecycle.LifecycleRequest.Retention;
import com.example.auditlog.lifecycle.LifecycleService;
import com.example.auditlog.persistence.AuditAppender;
import com.example.auditlog.persistence.AuditReadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

abstract class ExportContract {

    @Autowired
    private ExportService exports;
    @Autowired
    private AuditAppender appender;
    @Autowired
    private PayloadProtector protector;
    @Autowired
    private LifecycleService lifecycle;
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
    void sparseSelectionBridgesGenesisToHeadWithoutUnrelatedPayloadOrFieldKeys() throws Exception {
        seed("other", "first", "unrelated-private-payload");
        UUID chosen = seed("chosen", "resource", "visible-unprotected");
        seed("other", "last", "another-unrelated-payload");
        byte[] bundle = exports.export(filter("chosen", null, false));
        var result = ExportBundle.verify(bundle, ExportTestKeys.PAIR.getPublic());
        assertThat(result.selectedIds()).containsExactly(chosen);
        assertThat(result.headSequence()).isEqualTo(3);
        byte[] content = entry(bundle, "records.bin");
        assertThat(new String(content, StandardCharsets.ISO_8859_1)).contains("visible-unprotected", "$auditProtected")
                .doesNotContain("synthetic-sensitive", "unrelated-private-payload", "another-unrelated-payload", "wrapped_key");
        assertThat(indexOf(content, reads.find(chosen).orElseThrow().canonicalBytes())).isGreaterThanOrEqualTo(0);
    }

    @Test
    void emptyChainAndNoMatchesHaveVerifiableEmptyMembership() throws Exception {
        assertThat(ExportBundle.verify(exports.export(filter("none", null, false)), ExportTestKeys.PAIR.getPublic()).headSequence()).isZero();
        seed("other", "resource", "ordinary");
        var proof = ExportBundle.verify(exports.export(filter("none", null, false)), ExportTestKeys.PAIR.getPublic());
        assertThat(proof.selectedIds()).isEmpty();
        assertThat(proof.headSequence()).isEqualTo(1);
    }

    @Test
    void resourceAndActorFiltersCombineWithExactUnicodeEscapedStrings() throws Exception {
        String actor = "actor\"\\\n\u263a";
        UUID chosen = seed(actor, "r", "ordinary");
        seed("other", "r", "ordinary");
        assertThat(ExportBundle.verify(exports.export(filter(actor, "r", false)), ExportTestKeys.PAIR.getPublic()).selectedIds())
                .containsExactly(chosen);
        assertThat(ExportBundle.verify(exports.export(new ExportFilter(null, "r", "ACCOUNT", false)),
                ExportTestKeys.PAIR.getPublic()).selectedIds()).hasSize(2);
    }

    @Test
    void archiveAndErasureExportOriginalCiphertextAndSignedStatusWithoutKeys() throws Exception {
        UUID target = seed("chosen", "r", "ordinary");
        byte[] before = reads.find(target).orElseThrow().canonicalBytes();
        lifecycle.retain(new Retention(UUID.randomUUID(), "synthetic-policy"), "operator");
        assertThat(ExportBundle.verify(exports.export(filter("chosen", null, false)), ExportTestKeys.PAIR.getPublic()).selectedIds()).isEmpty();
        lifecycle.redact(target, new Redaction(UUID.randomUUID(), "privacy", "synthetic-approved", List.of("/accountNumber")), "operator");
        byte[] bundle = exports.export(filter("chosen", null, true));
        assertThat(ExportBundle.verify(bundle, ExportTestKeys.PAIR.getPublic()).selectedIds()).containsExactly(target);
        assertThat(indexOf(entry(bundle, "records.bin"), before)).isGreaterThanOrEqualTo(0);
        assertThat(new String(entry(bundle, "records.bin"), StandardCharsets.ISO_8859_1)).contains("/accountNumber")
                .doesNotContain("synthetic-sensitive");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payload_key WHERE event_id = ?", Integer.class, target)).isZero();
    }

    @Test
    void tamperingInUnselectedRecordIsNotSignedAsValidEvidence() {
        seed("chosen", "r", "ordinary");
        UUID other = seed("other", "r", "ordinary");
        jdbc.update("UPDATE audit_event SET actor_id = 'forged' WHERE event_id = ?", other);
        assertThatThrownBy(() -> exports.export(filter("chosen", null, false))).isInstanceOf(EventIntegrityException.class);
    }

    @Test
    void deletionAndHeadMismatchFailClosed() {
        UUID id = seed("other", "r", "ordinary");
        jdbc.update("DELETE FROM payload_key WHERE event_id = ?", id);
        jdbc.update("DELETE FROM audit_event WHERE event_id = ?", id);
        assertStatus(503);
        jdbc.update("UPDATE chain_head SET last_sequence = 0");
        assertStatus(503);
    }

    @Test
    void lifecycleProofTamperingFailsExportEvenWhenAdministrativePayloadIsNotSelected() {
        UUID target = seed("chosen", "r", "ordinary");
        lifecycle.redact(target, new Redaction(UUID.randomUUID(), "privacy", "synthetic-approved", List.of("/accountNumber")), "operator");
        jdbc.update("DELETE FROM payload_redaction WHERE event_id = ?", target);
        assertThatThrownBy(() -> exports.export(filter("chosen", null, false))).isInstanceOf(EventIntegrityException.class);
    }

    @Test
    void ciphertextExportDoesNotRequireDecryptionKeysOrInventErasureStatus() throws Exception {
        UUID target = seed("chosen", "r", "ordinary");
        jdbc.update("DELETE FROM payload_key WHERE event_id = ?", target);
        byte[] bundle = exports.export(filter("chosen", null, false));
        assertThat(ExportBundle.verify(bundle, ExportTestKeys.PAIR.getPublic()).selectedIds()).containsExactly(target);
        assertThat(new String(entry(bundle, "records.bin"), StandardCharsets.ISO_8859_1)).doesNotContain("/accountNumber");
    }

    @Test
    void selectionLimitRejectsWholeBundleAndCapacityIsReleased() throws Exception {
        seed("chosen", "r", "ordinary");
        seed("chosen", "r", "ordinary");
        seed("chosen", "r", "ordinary");
        assertStatus(413);
        assertThat(ExportBundle.verify(exports.export(filter("none", null, false)), ExportTestKeys.PAIR.getPublic()).selectedIds()).isEmpty();
    }

    @Test
    void proofLimitAndByteLimitRejectInsteadOfTruncating() {
        for (int index = 0; index < 21; index++) {
            seed("other", "r", "ordinary");
        }
        assertStatus(413);
        clearFixture();
        seed("chosen", "r", "x".repeat(5000));
        assertStatus(413);
    }

    @Test
    void concurrentAppendUsesOneSnapshotAndExcessExportReturns429() throws Exception {
        seed("chosen", "r", "ordinary");
        CountDownLatch captured = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            Object head = call.callRealMethod();
            captured.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test latch timeout");
            }
            return head;
        }).when(reads).head();
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = pool.submit(() -> exports.export(filter("chosen", null, false)));
            try {
                assertThat(captured.await(10, TimeUnit.SECONDS)).isTrue();
                assertStatus(429);
                seed("other", "r", "ordinary");
            } finally {
                release.countDown();
            }
            var proof = ExportBundle.verify(future.get(15, TimeUnit.SECONDS), ExportTestKeys.PAIR.getPublic());
            assertThat(proof.headSequence()).isEqualTo(1);
            assertThat(proof.selectedIds()).hasSize(1);
        } finally {
            reset(reads);
        }
        assertThat(ExportBundle.verify(exports.export(filter("chosen", null, false)), ExportTestKeys.PAIR.getPublic()).headSequence()).isEqualTo(2);
    }

    private void assertStatus(int status) {
        assertThatThrownBy(() -> exports.export(filter("chosen", null, false))).isInstanceOf(AuditQueryException.class)
                .satisfies(exception -> assertThat(((AuditQueryException) exception).status()).isEqualTo(status));
    }

    private ExportFilter filter(String actor, String resource, boolean archived) {
        return new ExportFilter(actor, resource, null, archived);
    }

    private UUID seed(String actor, String resource, String ordinary) {
        UUID id = UUID.randomUUID();
        Map<String, Object> payload = Map.of("ordinary", ordinary, "accountNumber", "synthetic-sensitive");
        appender.append(id, Instant.parse("2020-01-01T00:00:00Z"),
                new EventInput("READ", actor, "ACCOUNT", resource, payload, null), protector.protect(id, payload));
        return id;
    }

    static byte[] entry(byte[] zip, String name) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    return in.readAllBytes();
                }
            }
        }
        throw new IllegalArgumentException("Missing test entry");
    }

    private int indexOf(byte[] data, byte[] expected) {
        outer: for (int index = 0; index <= data.length - expected.length; index++) {
            for (int offset = 0; offset < expected.length; offset++) {
                if (data[index + offset] != expected[offset]) {
                    continue outer;
                }
            }
            return index;
        }
        return -1;
    }
}
