package com.example.auditlog.export;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import com.example.auditlog.audit.AuditQueryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ExportSigner {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public ExportSigner(@Value("${audit.export.private-key-base64:}") String configured) {
        if (configured.isBlank()) {
            privateKey = null;
            publicKey = null;
            return;
        }
        byte[] encoded = null;
        try {
            if (configured.length() > 16384) {
                throw new IllegalArgumentException();
            }
            encoded = Base64.getDecoder().decode(configured);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey key = (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
            if (key.getModulus().bitLength() < 2048 || key.getModulus().bitLength() > 8192) {
                throw new IllegalArgumentException();
            }
            privateKey = key;
            publicKey = factory.generatePublic(new RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Export signing requires a PKCS8 RSA 2048-8192 private key");
        } finally {
            if (encoded != null) {
                java.util.Arrays.fill(encoded, (byte) 0);
            }
        }
    }

    public PrivateKey privateKey() {
        if (privateKey == null) {
            throw new AuditQueryException(503, "Export signing is not configured");
        }
        return privateKey;
    }

    public PublicKey publicKey() {
        privateKey();
        return publicKey;
    }
}
