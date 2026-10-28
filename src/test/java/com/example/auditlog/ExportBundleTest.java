package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.example.auditlog.audit.CanonicalJson;
import com.example.auditlog.export.ExportBundle;
import com.example.auditlog.export.ExportBundle.Header;
import com.example.auditlog.export.ExportFilter;
import com.example.auditlog.export.ExportSigner;
import org.junit.jupiter.api.Test;

class ExportBundleTest {

    static byte[] fixture() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000006");
        CanonicalJson json = new CanonicalJson();
        byte[] bytes = json.bytes(Map.of("schemaVersion", 1, "sequence", 1, "eventId", id.toString(),
                "previousHash", "0".repeat(64), "actorId", "chosen", "resourceId", "r", "resourceType", "ACCOUNT",
                "payload", Map.of("sequence", 999, "actorId", "nested-spoof")));
        return ExportBundle.create(List.of(new Header(1, "0".repeat(64), json.hash(bytes), id, bytes, false, List.of())),
                "chosen", null, null, false, Instant.parse("2026-10-28T00:00:00Z"), 1, json.hash(bytes),
                ExportTestKeys.PAIR.getPrivate(), ExportTestKeys.PAIR.getPublic());
    }

    @Test
    void verifiesIndependentContentHashAndIgnoresNestedEnvelopeLookalikes() throws Exception {
        var result = ExportBundle.verify(fixture(), ExportTestKeys.PAIR.getPublic());
        assertThat(result.headSequence()).isEqualTo(1);
        assertThat(result.selectedIds()).containsExactly(UUID.fromString("00000000-0000-0000-0000-000000000006"));
    }

    @Test
    void wrongTrustKeyAndCorruptSignatureFail() throws Exception {
        byte[] bundle = fixture();
        assertThatThrownBy(() -> ExportBundle.verify(bundle, ExportTestKeys.pair().getPublic())).hasMessageContaining("signature");
        var entries = unpack(bundle);
        entries.get("signature.bin")[0] ^= 1;
        reject(entries, false);
    }

    @Test
    void contentAlterationFailsDigestThenHashEvenWithResignedManifest() throws Exception {
        var entries = unpack(fixture());
        byte[] body = entries.get("records.bin");
        body[body.length - 2] ^= 1;
        reject(entries, false);
        reject(entries, true);
    }

    @Test
    void missingOrReorderedHeadersFailEvenWithValidSignature() throws Exception {
        var entries = unpack(fixture());
        byte[] body = entries.get("records.bin");
        body[15] = 2; // Big-endian sequence 1 -> 2.
        reject(entries, true);
        entries.put("records.bin", java.util.Arrays.copyOf(body, 8));
        reject(entries, true);
    }

    @Test
    void alteredManifestUnsupportedVersionAndMembershipAreRejected() throws Exception {
        var entries = unpack(fixture());
        replace(entries, "actorId=Y2hvc2Vu", "actorId=b3RoZXI");
        reject(entries, false);
        reject(entries, true); // A trusted signature cannot repair wrong selected filter.
        entries = unpack(fixture());
        replace(entries, "AUDIT-EXPORT-V1", "AUDIT-EXPORT-V9");
        reject(entries, true);
        entries = unpack(fixture());
        replace(entries, "selectedIds=00000000-0000-0000-0000-000000000006", "selectedIds=");
        reject(entries, true);
    }

    @Test
    void headAndAlgorithmMismatchAreRejected() throws Exception {
        var entries = unpack(fixture());
        replace(entries, "headHash=", "headHash=0");
        reject(entries, true);
        entries = unpack(fixture());
        replace(entries, "SHA256withRSA", "NONE");
        reject(entries, true);
    }

    @Test
    void missingUnexpectedTruncatedAndOversizedEntriesAreRejected() throws Exception {
        var entries = unpack(fixture());
        entries.remove("signature.bin");
        reject(entries, false);
        entries = unpack(fixture());
        entries.put("../unexpected", new byte[0]);
        reject(entries, false);
        entries = unpack(fixture());
        entries.put("signature.bin", new byte[1025]);
        reject(entries, false);
        byte[] bundle = fixture();
        assertThatThrownBy(() -> ExportBundle.verify(java.util.Arrays.copyOf(bundle, 25), ExportTestKeys.PAIR.getPublic()))
                .isInstanceOf(Exception.class);
    }

    @Test
    void filtersRejectUnboundedUnknownRepeatedAndInvalidArchiveRequests() {
        for (var request : List.of(Map.<String, List<String>>of(), Map.of("actorId", List.of(" ")),
                Map.of("resourceType", List.of("ACCOUNT")), Map.of("actorId", List.of("a", "b")),
                Map.of("actorId", List.of("a"), "cursor", List.of("x")),
                Map.of("actorId", List.of("a"), "includeArchived", List.of("yes")))) {
            assertThatThrownBy(() -> ExportFilter.parse(request)).isInstanceOf(com.example.auditlog.audit.InvalidEventException.class);
        }
        assertThat(ExportFilter.parse(Map.of("actorId", List.of("a"), "includeArchived", List.of("true"))).includeArchived()).isTrue();
    }

    @Test
    void signerFailsClosedWhenMissingMalformedOrWeak() throws Exception {
        assertThatThrownBy(() -> new ExportSigner("").privateKey()).hasMessageContaining("not configured");
        assertThatThrownBy(() -> new ExportSigner("invalid")).hasMessageContaining("PKCS8");
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        String weak = java.util.Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        assertThatThrownBy(() -> new ExportSigner(weak)).hasMessageContaining("2048");
        assertThat(new ExportSigner(ExportTestKeys.privateKey()).publicKey()).isEqualTo(ExportTestKeys.PAIR.getPublic());
    }

    private static Map<String, byte[]> unpack(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private void replace(Map<String, byte[]> entries, String before, String after) {
        entries.put("manifest.txt", new String(entries.get("manifest.txt"), StandardCharsets.UTF_8)
                .replace(before, after).getBytes(StandardCharsets.UTF_8));
    }

    private void reject(Map<String, byte[]> entries, boolean resign) throws Exception {
        if (resign) {
            String manifest = new String(entries.get("manifest.txt"), StandardCharsets.UTF_8);
            manifest = manifest.replaceAll("bodySha256=[0-9a-f]+", "bodySha256=" + ExportBundle.digest(entries.get("records.bin")));
            entries.put("manifest.txt", manifest.getBytes(StandardCharsets.UTF_8));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(ExportTestKeys.PAIR.getPrivate());
            signature.update(entries.get("manifest.txt"));
            entries.put("signature.bin", signature.sign());
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        assertThatThrownBy(() -> ExportBundle.verify(buffer.toByteArray(), ExportTestKeys.PAIR.getPublic())).isInstanceOf(Exception.class);
    }
}
