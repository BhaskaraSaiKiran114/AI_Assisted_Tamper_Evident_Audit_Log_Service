package com.example.auditlog.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.auditlog.audit.QueryCursorCodec.Position;
import com.example.auditlog.audit.VerificationResult.ChainHead;
import com.example.auditlog.persistence.AuditReadRepository;
import com.example.auditlog.persistence.StoredEvent;
import com.example.auditlog.persistence.ReadSnapshot;
import com.example.auditlog.lifecycle.LifecycleEvidence;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {

    public record EventView(UUID eventId, long sequence, String eventType, String actorId, String resourceType,
            String resourceId, Object payload, Instant timestamp, Instant ingestedAt, String previousHash, String recordHash,
            boolean archived, List<String> redactedPaths) { }

    public record EventPage(List<EventView> events, int limit, String nextCursor, ChainHead snapshotHead) { }

    private final AuditReadRepository repository;
    private final EventIntegrityChecker checker;
    private final PayloadReader payloads;
    private final QueryCursorCodec cursors;
    private final ReadSnapshot snapshots;
    private final LifecycleEvidence lifecycle;

    public AuditQueryService(AuditReadRepository repository, EventIntegrityChecker checker,
            PayloadReader payloads, QueryCursorCodec cursors, ReadSnapshot snapshots, LifecycleEvidence lifecycle) {
        this.repository = repository;
        this.checker = checker;
        this.payloads = payloads;
        this.cursors = cursors;
        this.snapshots = snapshots;
        this.lifecycle = lifecycle;
    }

    public EventPage query(EventQuery query) {
        return snapshots.execute(30, () -> querySnapshot(query));
    }

    private EventPage querySnapshot(EventQuery query) {
        Position position;
        if (query.cursor() == null) {
            ChainHead head = repository.head().orElseThrow(() -> new AuditQueryException(503, "Chain head unavailable"));
            if (head.sequence() < 0 || !head.hash().matches("[0-9a-f]{64}")) {
                throw new AuditQueryException(503, "Chain head is invalid");
            }
            position = new Position(0, head.sequence(), head.hash());
        } else {
            position = cursors.decode(query.cursor(), query);
        }
        if (position.upper() > 0 && !repository.hashAt(position.upper()).orElse("").equals(position.headHash())
                || position.upper() == 0 && !position.headHash().equals("0".repeat(64))) {
            throw new AuditQueryException(409, "Pagination snapshot is no longer available");
        }
        List<StoredEvent> rows = repository.page(query, position.after(), position.upper());
        boolean more = rows.size() > query.limit();
        List<StoredEvent> selected = rows.subList(0, Math.min(rows.size(), query.limit()));
        List<EventView> events = selected.stream().map(this::view).toList();
        String next = more ? cursors.encode(new Position(selected.getLast().sequence(), position.upper(), position.headHash()), query)
                : null;
        return new EventPage(events, query.limit(), next, new ChainHead(position.upper(), position.headHash()));
    }

    public EventView find(UUID id) {
        return find(id, false);
    }

    public EventView find(UUID id, boolean includeArchived) {
        return snapshots.execute(30, () -> view(repository.find(id)
                .filter(event -> includeArchived || !lifecycle.archived(event.eventId()))
                .orElseThrow(() -> new AuditQueryException(404, "Audit event not found"))));
    }

    private EventView view(StoredEvent event) {
        try {
            var payload = payloads.read(event.eventId(), checker.check(event));
            return new EventView(event.eventId(), event.sequence(), event.eventType(), event.actorId(), event.resourceType(),
                    event.resourceId(), payload.value(), event.timestamp(), event.ingestedAt(), event.previousHash(), event.recordHash(),
                    lifecycle.archived(event.eventId()), payload.redactedPaths());
        } catch (EventIntegrityException exception) {
            throw new AuditQueryException(503, "Stored audit data failed integrity checks");
        }
    }
}
