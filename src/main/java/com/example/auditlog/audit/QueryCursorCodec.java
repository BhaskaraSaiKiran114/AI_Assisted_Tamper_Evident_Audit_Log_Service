package com.example.auditlog.audit;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QueryCursorCodec {

    public record Position(long after, long upper, String headHash) { }

    private final CanonicalJson canonical;
    private final StoredJson json;
    private final byte[] key;

    public QueryCursorCodec(CanonicalJson canonical, StoredJson json,
            @Value("${audit.query.cursor-key-base64:}") String configuredKey) {
        this.canonical = canonical;
        this.json = json;
        try {
            key = configuredKey.isBlank() ? new byte[32] : Base64.getDecoder().decode(configuredKey);
            if (configuredKey.isBlank()) {
                new SecureRandom().nextBytes(key);
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Cursor key must be Base64-encoded key material");
        }
        if (key.length != 32) {
            throw new IllegalArgumentException("Cursor signing key must be 32 bytes");
        }
    }

    public String encode(Position position, EventQuery query) {
        return encode(position, "AUDIT-QUERY-FILTER-V1", query.fingerprintFields());
    }

    public String encode(Position position, String domain, Map<String, Object> context) {
        byte[] payload = canonical.bytes(Map.of("version", 2, "after", position.after(), "upper", position.upper(),
                "headHash", position.headHash(), "filterHash", fingerprint(domain, context)));
        return encode(payload) + "." + encode(sign(payload));
    }

    public Position decode(String cursor, EventQuery query) {
        return decode(cursor, "AUDIT-QUERY-FILTER-V1", query.fingerprintFields());
    }

    public Position decode(String cursor, String domain, Map<String, Object> context) {
        try {
            if (!cursor.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+") || cursor.length() > 2048) {
                throw new IllegalArgumentException();
            }
            String[] parts = cursor.split("\\.");
            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(sign(payload), signature)) {
                throw new IllegalArgumentException();
            }
            Map<String, Object> fields = json.object(payload);
            if (!fields.keySet().equals(Set.of("version", "after", "upper", "headHash", "filterHash"))
                    || !BigInteger.TWO.equals(fields.get("version")) || !fingerprint(domain, context).equals(fields.get("filterHash"))) {
                throw new IllegalArgumentException();
            }
            long after = ((BigInteger) fields.get("after")).longValueExact();
            long upper = ((BigInteger) fields.get("upper")).longValueExact();
            String hash = (String) fields.get("headHash");
            if (after < 0 || upper < after || !hash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException();
            }
            return new Position(after, upper, hash);
        } catch (RuntimeException exception) {
            throw new InvalidEventException("Invalid cursor or cursor does not match these filters and limit");
        }
    }

    private String fingerprint(String domain, Map<String, Object> context) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((domain + "\0").getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest.digest(canonical.bytes(context)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private byte[] sign(byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cursor signing unavailable", exception);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
