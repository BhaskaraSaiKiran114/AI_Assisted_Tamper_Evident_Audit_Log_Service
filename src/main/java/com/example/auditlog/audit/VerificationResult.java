package com.example.auditlog.audit;

import java.util.UUID;

public record VerificationResult(boolean intact, long checkedCount, ChainHead snapshotHead, Inconsistency inconsistency) {

    public record ChainHead(long sequence, String hash) { }

    public enum ViolationType {
        CONTENT_HASH_MISMATCH, INDEX_FIELD_MISMATCH, PREVIOUS_HASH_MISMATCH, SEQUENCE_GAP,
        HEAD_MISMATCH, MALFORMED_CONTENT, UNSUPPORTED_SCHEMA_VERSION, LIFECYCLE_MISMATCH
    }

    public record Inconsistency(long sequence, UUID eventId, ViolationType type) { }
}
