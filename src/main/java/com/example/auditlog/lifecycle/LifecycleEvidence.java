package com.example.auditlog.lifecycle;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.auditlog.audit.CanonicalJson;
import com.example.auditlog.audit.EventIntegrityChecker;
import com.example.auditlog.audit.EventIntegrityException;
import com.example.auditlog.audit.StoredJson;
import com.example.auditlog.audit.VerificationResult.ViolationType;
import com.example.auditlog.persistence.AuditReadRepository;
import com.example.auditlog.persistence.LifecycleRepository;
import com.example.auditlog.persistence.LifecycleRepository.Action;
import com.example.auditlog.persistence.LifecycleRepository.Marker;
import com.example.auditlog.persistence.StoredEvent;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class LifecycleEvidence {

    private final LifecycleRepository repository;
    private final AuditReadRepository events;
    private final EventIntegrityChecker checker;
    private final StoredJson json;
    private final CanonicalJson canonical;

    public LifecycleEvidence(LifecycleRepository repository, AuditReadRepository events, EventIntegrityChecker checker,
            StoredJson json, CanonicalJson canonical) {
        this.repository = repository;
        this.events = events;
        this.checker = checker;
        this.json = json;
        this.canonical = canonical;
    }

    public void checkEvent(StoredEvent event) {
        var action = repository.actionForEvent(event.eventId());
        if (event.eventType().startsWith("AUDIT_INTERNAL_") || action.isPresent()) {
            validate(action.orElseThrow(this::invalid));
        }
    }

    public boolean archived(UUID id) {
        var marker = repository.archiveMarker(id);
        marker.ifPresent(value -> validate(repository.action(value.operationId()).orElseThrow(this::invalid)));
        return marker.isPresent();
    }

    public Map<String, Marker> redactions(UUID id) {
        List<Marker> markers = repository.redactionsForEvent(id);
        markers.stream().map(Marker::operationId).distinct()
                .forEach(op -> validate(repository.action(op).orElseThrow(this::invalid)));
        return markers.stream().collect(Collectors.toMap(Marker::path, value -> value));
    }

    public LifecycleReceipt validate(Action action) {
        try {
            StoredEvent audit = events.find(action.auditEventId()).orElseThrow(this::invalid);
            Map<String, Object> payload = checker.check(audit);
            Map<String, Object> detail = json.object(action.detailJson().getBytes(StandardCharsets.UTF_8));
            require(Arrays.equals(canonical.bytes(payload), canonical.bytes(detail))
                    && audit.eventType().equals("AUDIT_INTERNAL_" + action.type())
                    && audit.resourceType().equals("AUDIT_LIFECYCLE") && audit.resourceId().equals(action.operationId().toString())
                    && audit.actorId().equals(action.actor()) && audit.ingestedAt().equals(action.completedAt())
                    && "audit-lifecycle-v1".equals(detail.get("protocol"))
                    && action.operationId().toString().equals(detail.get("operationId"))
                    && action.type().equals(detail.get("action")) && action.actor().equals(detail.get("actorId"))
                    && action.requestHash().equals(detail.get("requestHash"))
                    && action.completedAt().toString().equals(detail.get("completedAt")));
            List<UUID> targets = strings(detail, "affectedEventIds").stream().map(UUID::fromString).toList();
            List<String> paths = strings(detail, "affectedPaths");
            Instant cutoff = detail.get("cutoff") == null ? null : Instant.parse((String) detail.get("cutoff"));
            if (action.type().equals("RETENTION")) {
                List<Marker> markers = repository.archivesForOperation(action.operationId());
                require(cutoff != null && paths.isEmpty() && targets.size() == Set.copyOf(targets).size()
                        && markers.size() == targets.size() && markers.stream().allMatch(m -> targets.contains(m.eventId())
                        && m.at().equals(action.completedAt())) && repository.redactionsForOperation(action.operationId()).isEmpty());
                for (UUID id : targets) {
                    var target = events.find(id).orElseThrow(this::invalid);
                    checker.check(target);
                    require(target.ingestedAt().isBefore(cutoff) && !target.eventType().startsWith("AUDIT_INTERNAL_"));
                }
            } else {
                UUID target = UUID.fromString((String) detail.get("targetEventId"));
                require(action.type().equals("REDACTION") && targets.equals(List.of(target))
                        && paths.size() == Set.copyOf(paths).size() && repository.archivesForOperation(action.operationId()).isEmpty());
                List<Marker> markers = repository.redactionsForOperation(action.operationId());
                var committed = checker.check(events.find(target).orElseThrow(this::invalid));
                var protectedFields = ProtectedFields.collect(committed);
                var keys = events.wrappedKeys(target);
                require(markers.size() == paths.size() && markers.stream().allMatch(m -> m.eventId().equals(target)
                        && paths.contains(m.path()) && m.at().equals(action.completedAt()))
                        && paths.stream().allMatch(path -> protectedFields.containsKey(path) && !keys.containsKey(path)));
            }
            int count = action.type().equals("RETENTION") ? targets.size() : paths.size();
            require(detail.get("affectedCount").toString().equals(Integer.toString(count)));
            return new LifecycleReceipt(action.operationId(), action.type(), action.auditEventId(), count, action.completedAt(),
                    cutoff, (String) detail.get("policyVersion"), (Boolean) detail.get("hasMore"), targets, paths);
        } catch (DataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private List<String> strings(Map<String, Object> detail, String key) {
        return ((List<?>) detail.get(key)).stream().map(String.class::cast).toList();
    }

    private void require(boolean condition) {
        if (!condition) {
            throw invalid();
        }
    }

    private EventIntegrityException invalid() {
        return new EventIntegrityException(ViolationType.LIFECYCLE_MISMATCH);
    }
}
