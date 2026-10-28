package com.example.auditlog.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Portable Java 21 format/verifier: deliberately no framework, database or JSON-library dependency. */
public final class ExportBundle {

    public static final int MAX_BODY = 16 * 1024 * 1024;
    public static final int MAX_RECORDS = 10000;
    public static final int MAX_SELECTED = 1000;
    private static final String ZERO = "0".repeat(64);
    private static final List<String> FIELDS = List.of("format", "canonicalization", "signatureAlgorithm", "createdAt",
            "actorId", "resourceId", "resourceType", "includeArchived", "headSequence", "headHash", "selectedIds",
            "bodySha256", "keyFingerprint");

    private ExportBundle() { }

    public record Header(long sequence, String previousHash, String recordHash, UUID eventId,
            byte[] canonical, boolean archived, List<String> redactedPaths) { }

    public record Verified(long headSequence, String headHash, List<UUID> selectedIds, Instant createdAt) { }

    public static byte[] create(List<Header> headers, String actor, String resource, String type, boolean archived,
            Instant created, long head, String headHash, PrivateKey privateKey, PublicKey publicKey)
            throws IOException, GeneralSecurityException {
        require(headers.size() <= MAX_RECORDS, "Too many proof headers");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        List<String> ids = new ArrayList<>();
        try (DataOutputStream body = new DataOutputStream(buffer)) {
            body.writeInt(1);
            body.writeInt(headers.size());
            for (Header header : headers) {
                body.writeLong(header.sequence());
                body.write(HexFormat.of().parseHex(header.previousHash()));
                body.write(HexFormat.of().parseHex(header.recordHash()));
                body.writeBoolean(header.canonical() != null);
                if (header.canonical() != null) {
                    ids.add(header.eventId().toString());
                    require(ids.size() <= MAX_SELECTED, "Too many selected records");
                    body.writeUTF(header.eventId().toString());
                    body.writeBoolean(header.archived());
                    body.writeInt(header.redactedPaths().size());
                    for (String path : header.redactedPaths()) {
                        body.writeUTF(path);
                    }
                    body.writeInt(header.canonical().length);
                    body.write(header.canonical());
                }
                require(buffer.size() <= MAX_BODY, "Export body exceeds limit");
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        List<String> data = List.of("AUDIT-EXPORT-V1", "AUDIT-CHAIN-V1", "SHA256withRSA", created.toString(),
                text(actor), text(resource), text(type), Boolean.toString(archived), Long.toString(head), headHash,
                String.join(",", ids), digest(buffer.toByteArray()), digest(publicKey.getEncoded()));
        for (int index = 0; index < FIELDS.size(); index++) {
            values.put(FIELDS.get(index), data.get(index));
        }
        byte[] manifest = manifest(values);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(manifest);
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip)) {
            entry(out, "manifest.txt", manifest);
            entry(out, "records.bin", buffer.toByteArray());
            entry(out, "signature.bin", signer.sign());
        }
        byte[] result = zip.toByteArray();
        verify(result, publicKey); // Never emit a bundle the independent reader rejects.
        return result;
    }

    public static Verified verify(byte[] zip, PublicKey trustedKey) throws IOException, GeneralSecurityException {
        require(trustedKey instanceof RSAKey rsa && rsa.getModulus().bitLength() >= 2048
                && rsa.getModulus().bitLength() <= 8192, "Trusted key must be RSA 2048-8192");
        require(zip.length <= MAX_BODY + 1024 * 1024, "Bundle exceeds limit");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry item;
            while ((item = in.getNextEntry()) != null) {
                String name = item.getName();
                require(List.of("manifest.txt", "records.bin", "signature.bin").contains(name)
                        && !entries.containsKey(name) && !item.isDirectory(), "Unexpected or duplicate ZIP entry");
                int cap = name.equals("records.bin") ? MAX_BODY : name.equals("manifest.txt") ? 65536 : 1024;
                byte[] bytes = in.readNBytes(cap + 1);
                require(bytes.length <= cap, "ZIP entry exceeds limit");
                entries.put(name, bytes);
                in.closeEntry();
            }
        }
        require(entries.size() == 3, "Missing ZIP entry");
        byte[] manifest = entries.get("manifest.txt");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(trustedKey);
        signature.update(manifest);
        require(signature.verify(entries.get("signature.bin")), "Invalid signature or wrong trusted key");
        Map<String, String> values = new LinkedHashMap<>();
        String[] lines = new String(manifest, StandardCharsets.UTF_8).split("\n", -1);
        require(lines.length == FIELDS.size() + 1 && lines[lines.length - 1].isEmpty(), "Invalid manifest");
        for (int index = 0; index < FIELDS.size(); index++) {
            String prefix = FIELDS.get(index) + "=";
            require(lines[index].startsWith(prefix), "Invalid manifest field/order");
            values.put(FIELDS.get(index), lines[index].substring(prefix.length()));
        }
        require(Arrays.equals(manifest, manifest(values)), "Noncanonical manifest");
        require(values.get("format").equals("AUDIT-EXPORT-V1") && values.get("canonicalization").equals("AUDIT-CHAIN-V1")
                && values.get("signatureAlgorithm").equals("SHA256withRSA"), "Unsupported version/algorithm");
        require(values.get("keyFingerprint").equals(digest(trustedKey.getEncoded())), "Wrong key fingerprint");
        byte[] body = entries.get("records.bin");
        require(values.get("bodySha256").equals(digest(body)), "Body digest mismatch");
        require(List.of("true", "false").contains(values.get("includeArchived")), "Invalid archive mode");
        boolean includeArchived = Boolean.parseBoolean(values.get("includeArchived"));
        String actor = untext(values.get("actorId"));
        String resource = untext(values.get("resourceId"));
        String type = untext(values.get("resourceType"));
        require(actor != null || resource != null, "Missing selection filter");
        require(type == null || resource != null, "Resource type requires resource ID");
        long head = Long.parseLong(values.get("headSequence"));
        require(head >= 0 && head <= MAX_RECORDS, "Unsupported head range");
        List<UUID> ids = new ArrayList<>();
        String previous = ZERO;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            require(in.readInt() == 1 && in.readInt() == head, "Invalid proof version/count");
            for (long sequence = 1; sequence <= head; sequence++) {
                require(in.readLong() == sequence, "Missing/reordered proof header");
                String predecessor = hex(in);
                String hash = hex(in);
                require(predecessor.equals(previous), "Broken predecessor link");
                int selected = in.readUnsignedByte();
                require(selected <= 1, "Invalid membership flag");
                if (selected == 1) {
                    UUID id = UUID.fromString(in.readUTF());
                    require(!ids.contains(id) && ids.size() < MAX_SELECTED, "Duplicate/excess selected ID");
                    int archived = in.readUnsignedByte();
                    require(archived <= 1 && (includeArchived || archived == 0), "Invalid archived membership");
                    int paths = in.readInt();
                    require(paths >= 0 && paths <= 1000, "Invalid redaction path count");
                    List<String> redacted = new ArrayList<>();
                    for (int index = 0; index < paths; index++) {
                        String path = in.readUTF();
                        require(path.startsWith("/") && path.length() <= 1024 && !redacted.contains(path), "Invalid redaction path");
                        redacted.add(path);
                    }
                    int size = in.readInt();
                    require(size > 0 && size <= MAX_BODY && size <= in.available(), "Invalid selected content size");
                    byte[] canonical = in.readNBytes(size);
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    digest.update("AUDIT-CHAIN-V1\0".getBytes(StandardCharsets.US_ASCII));
                    digest.update(ByteBuffer.allocate(4).putInt(size).array());
                    require(hash.equals(HexFormat.of().formatHex(digest.digest(canonical))), "Selected content hash mismatch");
                    Map<String, String> fields = envelopeFields(canonical);
                    require("1".equals(fields.get("schemaVersion")) && Long.toString(sequence).equals(fields.get("sequence"))
                            && id.toString().equals(fields.get("eventId")) && predecessor.equals(fields.get("previousHash")),
                            "Selected envelope/header mismatch");
                    require((actor == null || actor.equals(fields.get("actorId")))
                            && (resource == null || resource.equals(fields.get("resourceId")))
                            && (type == null || type.equals(fields.get("resourceType"))), "Selected filter mismatch");
                    ids.add(id);
                }
                previous = hash;
            }
            require(in.read() == -1, "Unexpected proof data");
        }
        require(previous.equals(values.get("headHash")), "Snapshot head mismatch");
        require(String.join(",", ids.stream().map(UUID::toString).toList()).equals(values.get("selectedIds")), "Membership mismatch");
        return new Verified(head, previous, List.copyOf(ids), Instant.parse(values.get("createdAt")));
    }

    /** Read top-level scalar commitments without mistaking nested payload keys for envelope fields. */
    private static Map<String, String> envelopeFields(byte[] canonical) throws IOException {
        String json = new String(canonical, StandardCharsets.UTF_8);
        Map<String, String> fields = new LinkedHashMap<>();
        int depth = 0;
        for (int index = 0; index < json.length(); index++) {
            char c = json.charAt(index);
            if (c == '"') {
                int end = stringEnd(json, index);
                if (depth == 1 && (index == 1 || json.charAt(index - 1) == ',')) {
                    String key = stringValue(json.substring(index + 1, end));
                    require(end + 2 < json.length() && json.charAt(end + 1) == ':', "Invalid envelope");
                    int start = end + 2;
                    if (json.charAt(start) == '"') {
                        int valueEnd = stringEnd(json, start);
                        require(fields.put(key, stringValue(json.substring(start + 1, valueEnd))) == null, "Duplicate field");
                        end = valueEnd;
                    } else if (Character.isDigit(json.charAt(start))) {
                        int valueEnd = start;
                        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
                            valueEnd++;
                        }
                        require(fields.put(key, json.substring(start, valueEnd)) == null, "Duplicate field");
                    }
                }
                index = end;
            } else if (c == '{' || c == '[') {
                depth++;
                require(depth <= 33, "Excessive envelope depth");
            } else if (c == '}' || c == ']') {
                depth--;
                require(depth >= 0, "Invalid envelope depth");
            }
        }
        require(depth == 0 && json.startsWith("{") && json.endsWith("}"), "Invalid envelope");
        return fields;
    }

    private static int stringEnd(String text, int start) throws IOException {
        for (int index = start + 1; index < text.length(); index++) {
            if (text.charAt(index) == '\\') {
                index++;
            } else if (text.charAt(index) == '"') {
                return index;
            }
        }
        throw new IOException("Unterminated string");
    }

    private static String stringValue(String text) throws IOException {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == '\\') {
                require(++index < text.length(), "Invalid escape");
                c = text.charAt(index);
                if (c == 'u') {
                    require(index + 4 < text.length(), "Invalid Unicode escape");
                    out.append((char) Integer.parseInt(text.substring(index + 1, index + 5), 16));
                    index += 4;
                } else {
                    require(c == '"' || c == '\\', "Unsupported canonical escape");
                    out.append(c);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String hex(DataInputStream in) throws IOException {
        byte[] bytes = in.readNBytes(32);
        require(bytes.length == 32, "Truncated hash");
        return HexFormat.of().formatHex(bytes);
    }

    public static String digest(byte[] bytes) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] manifest(Map<String, String> values) {
        StringBuilder out = new StringBuilder();
        FIELDS.forEach(key -> out.append(key).append('=').append(values.get(key)).append('\n'));
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String text(String value) {
        return value == null ? "-" : Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String untext(String value) throws IOException {
        if (value.equals("-")) {
            return null;
        }
        String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        require(!decoded.isBlank() && decoded.length() <= 255 && text(decoded).equals(value), "Invalid filter encoding");
        return decoded;
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }

    public static void main(String[] args) {
        try {
            require(args.length == 2, "Usage: java -jar audit-log-service-*-verifier.jar bundle.zip trusted-public-key-base64.txt");
            Path bundle = Path.of(args[0]);
            Path key = Path.of(args[1]);
            require(Files.size(bundle) <= MAX_BODY + 1024 * 1024 && Files.size(key) <= 16384, "Input exceeds limit");
            PublicKey trusted = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(
                    Base64.getDecoder().decode(Files.readString(key, StandardCharsets.US_ASCII).trim())));
            Verified verified = verify(Files.readAllBytes(bundle), trusted);
            System.out.println("VERIFIED selected=" + verified.selectedIds().size() + " head=" + verified.headSequence()
                    + " hash=" + verified.headHash() + " createdAt=" + verified.createdAt());
        } catch (Exception exception) {
            System.err.println("INVALID: bundle, format or trusted key verification failed");
            System.exit(1);
        }
    }
}
