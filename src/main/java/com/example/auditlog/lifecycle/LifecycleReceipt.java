package com.example.auditlog.lifecycle;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LifecycleReceipt(UUID operationId, String action, UUID auditEventId, int affectedCount,
        Instant completedAt, Instant cutoff, String policyVersion, boolean hasMore,
        List<UUID> affectedEventIds, List<String> affectedPaths) {
}
