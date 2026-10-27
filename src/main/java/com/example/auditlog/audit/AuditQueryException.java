package com.example.auditlog.audit;

public class AuditQueryException extends RuntimeException {

    private final int status;

    public AuditQueryException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
