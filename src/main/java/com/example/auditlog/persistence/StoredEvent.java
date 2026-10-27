package com.example.auditlog.persistence;

import java.time.Instant;
import java.util.UUID;

public record StoredEvent(UUID eventId, long sequence, String eventType, String actorId,
        String resourceType, String resourceId, Instant timestamp, Instant ingestedAt,
        int schemaVersion, byte[] canonicalBytes, String payloadJson, String previousHash, String recordHash) {
}
