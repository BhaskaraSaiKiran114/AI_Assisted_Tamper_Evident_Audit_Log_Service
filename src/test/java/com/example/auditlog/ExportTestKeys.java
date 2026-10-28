package com.example.auditlog;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

final class ExportTestKeys {
    static final KeyPair PAIR = pair();

    private ExportTestKeys() { }

    static KeyPair pair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String privateKey() {
        return Base64.getEncoder().encodeToString(PAIR.getPrivate().getEncoded());
    }
}
