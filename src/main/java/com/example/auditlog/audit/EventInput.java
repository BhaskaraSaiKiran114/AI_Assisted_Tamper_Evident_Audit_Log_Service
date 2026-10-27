package com.example.auditlog.audit;

import java.time.Instant;
import java.util.Map;

public record EventInput(String eventType, String actorId, String resourceType, String resourceId,
        Map<String, Object> payload, Instant timestamp) {
}
