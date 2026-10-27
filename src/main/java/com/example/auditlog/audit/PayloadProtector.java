package com.example.auditlog.audit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PayloadProtector {

    public record ProtectedPayload(Object payload, Map<String, String> wrappedKeys) { }

    private final CanonicalJson canonical;
    private final Set<String> paths;
    private final byte[] masterKey;
    private final SecureRandom random = new SecureRandom();

    public PayloadProtector(CanonicalJson canonical,
            @Value("${audit.crypto.master-key-base64:}") String configuredKey,
            @Value("${audit.crypto.ephemeral-master-key:false}") boolean ephemeral,
            @Value("${audit.payload.protected-paths:/accountNumber,/personalIdentifier}") String configuredPaths) {
        this.canonical = canonical;
        this.paths = configuredPaths.isBlank() ? Set.of() : Set.of(configuredPaths.split(",", -1));
        for (String path : paths) {
            if (!path.startsWith("/") || path.length() > 1024
                    || java.util.regex.Pattern.compile("~(?![01])").matcher(path).find()) {
                throw new IllegalArgumentException("Protected paths must be bounded RFC 6901 pointers");
            }
            if (paths.stream().anyMatch(other -> !other.equals(path) && other.startsWith(path + "/"))) {
                throw new IllegalArgumentException("Protected paths must not overlap");
            }
        }
        try {
            masterKey = configuredKey.isBlank() && ephemeral ? randomBytes(32)
                    : Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Master key must be Base64-encoded AES-256 key material");
        }
        if (masterKey.length != 32) {
            throw new IllegalArgumentException("Configure a 32-byte master key outside the database");
        }
    }

    public ProtectedPayload protect(UUID eventId, Map<String, Object> payload) {
        Map<String, String> wrapped = new LinkedHashMap<>();
        return new ProtectedPayload(visit(eventId, payload, "", wrapped), Map.copyOf(wrapped));
    }

    private Object visit(UUID eventId, Object value, String path, Map<String, String> wrapped) {
        if (paths.contains(path)) {
            byte[] fieldKey = randomBytes(32);
            try {
                byte[] nonce = randomBytes(12);
                byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, fieldKey, nonce, aad("value", eventId, path),
                        canonical.bytes(value));
                byte[] wrapNonce = randomBytes(12);
                byte[] keyCiphertext = crypt(Cipher.ENCRYPT_MODE, masterKey, wrapNonce, aad("key", eventId, path), fieldKey);
                wrapped.put(path, encode(wrapNonce) + "." + encode(keyCiphertext));
                return Map.of("$auditProtected", Map.of("version", 1, "algorithm", "AES-256-GCM",
                        "nonce", encode(nonce), "ciphertext", encode(ciphertext)));
            } finally {
                java.util.Arrays.fill(fieldKey, (byte) 0);
            }
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put((String) key,
                    visit(eventId, item, path + "/" + escape((String) key), wrapped)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (int index = 0; index < list.size(); index++) {
                result.add(visit(eventId, list.get(index), path + "/" + index, wrapped));
            }
            return result;
        }
        return value;
    }

    public byte[] decrypt(UUID eventId, String path, Map<?, ?> envelope, String wrappedKey) {
        String[] parts = wrappedKey.split("\\.", -1);
        if (parts.length != 2) {
            throw new IllegalStateException("Invalid wrapped field key");
        }
        byte[] key = crypt(Cipher.DECRYPT_MODE, masterKey, Base64.getDecoder().decode(parts[0]),
                aad("key", eventId, path), Base64.getDecoder().decode(parts[1]));
        try {
            return crypt(Cipher.DECRYPT_MODE, key, Base64.getDecoder().decode((String) envelope.get("nonce")),
                    aad("value", eventId, path), Base64.getDecoder().decode((String) envelope.get("ciphertext")));
        } finally {
            java.util.Arrays.fill(key, (byte) 0);
        }
    }

    private byte[] crypt(int mode, byte[] key, byte[] nonce, byte[] associated, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(associated);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Payload cryptographic operation failed", exception);
        }
    }

    private byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        random.nextBytes(bytes);
        return bytes;
    }

    private String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private byte[] aad(String kind, UUID id, String path) {
        return ("AUDIT-FIELD-V1\0" + kind + "\0" + id + "\0" + path).getBytes(StandardCharsets.UTF_8);
    }

    private String escape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }
}
