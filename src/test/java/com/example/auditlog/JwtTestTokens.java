package com.example.auditlog;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.auditlog.audit.CanonicalJson;

final class JwtTestTokens {

    static final KeyPair KEYS = keyPair();

    private JwtTestTokens() { }

    static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String publicKey() {
        return Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded());
    }

    static Map<String, Object> claims(String... permissions) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "urn:audit-log:local");
        claims.put("aud", List.of("audit-log-service"));
        claims.put("sub", "synthetic-producer");
        claims.put("iat", now - 5);
        claims.put("nbf", now - 5);
        claims.put("exp", now + 300);
        claims.put("permissions", List.of(permissions));
        return claims;
    }

    static String token(String... permissions) {
        return sign(claims(permissions), KEYS.getPrivate(), "RS256");
    }

    static String sign(Map<String, Object> claims, PrivateKey key, String algorithm) {
        try {
            CanonicalJson json = new CanonicalJson();
            String unsigned = encode(json.bytes(Map.of("alg", algorithm, "typ", "JWT"))) + "." + encode(json.bytes(claims));
            if (algorithm.equals("none")) {
                return unsigned + ".";
            }
            Signature signer = Signature.getInstance(algorithm.equals("RS512") ? "SHA512withRSA" : "SHA256withRSA");
            signer.initSign(key);
            signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return unsigned + "." + encode(signer.sign());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
