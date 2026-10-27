package com.example.auditlog.audit;

import com.example.auditlog.audit.VerificationResult.ViolationType;

public class EventIntegrityException extends RuntimeException {

    private final ViolationType type;

    public EventIntegrityException(ViolationType type) {
        super("Stored event integrity check failed");
        this.type = type;
    }

    public ViolationType type() {
        return type;
    }
}
