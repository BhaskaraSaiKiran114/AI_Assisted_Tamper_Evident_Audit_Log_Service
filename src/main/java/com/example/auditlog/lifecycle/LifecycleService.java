package com.example.auditlog.lifecycle;

import com.example.auditlog.security.ResourceScope;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.auditlog.audit.AuditQueryException;
import com.example.auditlog.audit.CanonicalJson;
import com.example.auditlog.audit.EventInput;
import com.example.auditlog.audit.EventIntegrityChecker;
import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.audit.PayloadProtector;
import com.example.auditlog.audit.PayloadProtector.ProtectedPayload;
import com.example.auditlog.lifecycle.LifecycleRequest.Redaction;
import com.example.auditlog.lifecycle.LifecycleRequest.Retention;
import com.example.auditlog.persistence.AuditAppender;
import com.example.auditlog.persistence.AuditReadRepository;
import com.example.auditlog.persistence.LifecycleRepository;
import com.example.auditlog.persistence.LifecycleRepository.Action;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LifecycleService {

    private final LifecycleRepository repository;
    private final AuditReadRepository events;
    private final LifecycleEvidence evidence;
    private final EventIntegrityChecker checker;
    private final AuditAppender appender;
    private final PayloadProtector protector;
    private final CanonicalJson canonical;
    private final Duration window;
    private final String policy;
    private final int batchSize;
    private final Clock clock;

    public LifecycleService(LifecycleRepository repository, AuditReadRepository events, LifecycleEvidence evidence,
            EventIntegrityChecker checker, AuditAppender appender, PayloadProtector protector, CanonicalJson canonical,
            @Value("${audit.retention.window:P90D}") Duration window,
            @Value("${audit.retention.policy-version:v1}") String policy,
            @Value("${audit.retention.batch-size:100}") int batchSize, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.evidence = evidence;
        this.checker = checker;
        this.appender = appender;
        this.protector = protector;
        this.canonical = canonical;
        if (window.isNegative() || window.isZero() || window.compareTo(Duration.ofDays(36500)) > 0
                || window.getNano() != 0 || policy.isBlank() || policy.length() > 64 || batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("Retention requires positive whole seconds <=100 years, a policy version and batch size 1-500");
        }
        this.window = window;
        this.policy = policy;
        this.batchSize = batchSize;
        this.clock = clock;
    }

    @Transactional(timeout = 30)
    public LifecycleReceipt retain(Retention request, String actor) {
        String fingerprint = fingerprint(Map.of("action", "RETENTION", "actorId", actor, "reason", request.reason(),
                "policyVersion", policy, "windowSeconds", window.getSeconds(), "batchSize", batchSize));
        repository.lockHead();
        var existing = repository.action(request.operationId());
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant cutoff = now.minus(window);
        List<UUID> candidates = repository.eligible(cutoff, batchSize + 1);
        boolean more = candidates.size() > batchSize;
        List<UUID> targets = candidates.subList(0, Math.min(candidates.size(), batchSize));
        targets.forEach(id -> checker.check(events.find(id).orElseThrow(() -> new AuditQueryException(503, "Retention target unavailable"))));
        Map<String, Object> detail = common(request.operationId(), "RETENTION", actor, request.reason(), fingerprint, now);
        detail.put("policyVersion", policy);
        detail.put("windowSeconds", window.getSeconds());
        detail.put("batchSize", batchSize);
        detail.put("cutoff", cutoff.toString());
        detail.put("hasMore", more);
        detail.put("affectedEventIds", targets.stream().map(UUID::toString).toList());
        detail.put("affectedPaths", List.of());
        detail.put("affectedCount", targets.size());
        detail.put("targetEventId", null);
        detail.put("requestedPaths", List.of());
        detail.put("approvalReference", null);
        Action action = record(request.operationId(), "RETENTION", actor, fingerprint, now, detail);
        targets.forEach(id -> repository.archive(id, action.operationId(), now));
        return evidence.validate(action);
    }

    @Transactional(timeout = 30)
    public LifecycleReceipt redact(UUID eventId, Redaction request, String actor) {
        return redact(eventId, request, actor, ResourceScope.internal());
    }

    @Transactional(timeout = 30)
    public LifecycleReceipt redact(UUID eventId, Redaction request, String actor, ResourceScope scope) {
        var authorized = events.find(eventId).filter(event -> scope.permits(event.resourceType(), event.resourceId()))
                .orElseThrow(() -> new AuditQueryException(404, "Audit event not found"));
        String fingerprint = fingerprint(Map.of("action", "REDACTION", "actorId", actor, "eventId", eventId.toString(),
                "reason", request.reason(), "approvalReference", request.approvalReference(), "paths", request.paths()));
        repository.lockHead();
        var existing = repository.action(request.operationId());
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }
        var target = authorized;
        if (target.eventType().startsWith("AUDIT_INTERNAL_")) {
            throw new InvalidEventException("Lifecycle evidence cannot be redacted");
        }
        var fields = ProtectedFields.collect(checker.check(target));
        if (!fields.keySet().containsAll(request.paths())) {
            throw new InvalidEventException("Only payload fields protected at ingestion can be redacted");
        }
        var oldMarkers = evidence.redactions(eventId);
        var keys = events.wrappedKeys(eventId);
        List<String> affected = request.paths().stream().filter(path -> !oldMarkers.containsKey(path)).toList();
        for (String path : affected) {
            if (!keys.containsKey(path)) {
                throw new AuditQueryException(503, "Protected payload is unavailable without redaction evidence");
            }
            try {
                byte[] plaintext = protector.decrypt(eventId, path, fields.get(path), keys.get(path));
                Arrays.fill(plaintext, (byte) 0);
            } catch (RuntimeException exception) {
                throw new AuditQueryException(503, "Protected payload is unavailable");
            }
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Map<String, Object> detail = common(request.operationId(), "REDACTION", actor, request.reason(), fingerprint, now);
        detail.put("policyVersion", null);
        detail.put("cutoff", null);
        detail.put("hasMore", false);
        detail.put("targetEventId", eventId.toString());
        detail.put("affectedEventIds", List.of(eventId.toString()));
        detail.put("requestedPaths", request.paths());
        detail.put("affectedPaths", affected);
        detail.put("affectedCount", affected.size());
        detail.put("approvalReference", request.approvalReference());
        Action action = record(request.operationId(), "REDACTION", actor, fingerprint, now, detail);
        for (String path : affected) {
            repository.markRedacted(eventId, path, request.operationId(), now);
            if (repository.eraseKey(eventId, path, request.operationId()) != 1) {
                throw new AuditQueryException(503, "Redaction key erasure did not complete");
            }
        }
        return evidence.validate(action);
    }

    private Map<String, Object> common(UUID id, String type, String actor, String reason, String hash, Instant now) {
        if (actor == null || actor.isBlank() || actor.length() > 255) {
            throw new InvalidEventException("Lifecycle operator is required");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("protocol", "audit-lifecycle-v1");
        detail.put("operationId", id.toString());
        detail.put("action", type);
        detail.put("actorId", actor);
        detail.put("reason", reason);
        detail.put("requestHash", hash);
        detail.put("completedAt", now.toString());
        return detail;
    }

    private Action record(UUID id, String type, String actor, String hash, Instant now, Map<String, Object> detail) {
        // Administrative evidence intentionally contains no sensitive payload values or wrapped keys.
        var receipt = appender.append(UUID.randomUUID(), now,
                new EventInput("AUDIT_INTERNAL_" + type, actor, "AUDIT_LIFECYCLE", id.toString(), detail, now),
                new ProtectedPayload(detail, Map.of()));
        Action action = new Action(id, type, actor, hash, receipt.eventId(), now,
                new String(canonical.bytes(detail), StandardCharsets.UTF_8));
        repository.insertAction(action);
        return action;
    }

    private LifecycleReceipt replay(Action action, String fingerprint) {
        if (!action.requestHash().equals(fingerprint)) {
            throw new AuditQueryException(409, "Operation ID is already bound to another lifecycle request");
        }
        return evidence.validate(action);
    }

    private String fingerprint(Map<String, Object> request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("AUDIT-LIFECYCLE-REQUEST-V1\0".getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest.digest(canonical.bytes(request)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
