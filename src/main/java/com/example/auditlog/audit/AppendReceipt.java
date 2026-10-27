package com.example.auditlog.audit;

import java.time.Instant;
import java.util.UUID;

public record AppendReceipt(UUID eventId, long sequence, Instant timestamp, Instant ingestedAt,
        String previousHash, String recordHash) {
}
