package com.example.auditlog.audit;

public class InvalidEventException extends RuntimeException {

    private final int status;

    public InvalidEventException(String message) {
        this(message, 400);
    }

    public InvalidEventException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
