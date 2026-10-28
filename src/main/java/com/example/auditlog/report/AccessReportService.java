package com.example.auditlog.report;

import com.example.auditlog.security.ResourceScope;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import com.example.auditlog.audit.AuditQueryException;
import com.example.auditlog.audit.EventIntegrityChecker;
import com.example.auditlog.audit.EventIntegrityException;
import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.audit.QueryCursorCodec;
import com.example.auditlog.audit.QueryCursorCodec.Position;
import com.example.auditlog.audit.VerificationResult.ChainHead;
import com.example.auditlog.lifecycle.LifecycleEvidence;
import com.example.auditlog.persistence.AuditReadRepository;
import com.example.auditlog.persistence.ReadSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AccessReportService {

    private static final String DOMAIN = "AUDIT-ACCESS-REPORT-CURSOR-V1";
    public record Entry(UUID eventId, long sequence, String actorId, String accountId, String action,
            String outcome, Instant occurredAt, boolean archived) { }
    public record Report(String reportVersion, List<Entry> entries, int limit, String nextCursor,
            ChainHead snapshotHead, int scannedCount, int excludedOutcomeCount) { }

    private final AuditReadRepository repository;
    private final EventIntegrityChecker checker;
    private final LifecycleEvidence lifecycle;
    private final ReadSnapshot snapshots;
    private final QueryCursorCodec cursors;
    private final Duration maxWindow;
    private final int scanLimit;
    private final Semaphore permits;

    public AccessReportService(AuditReadRepository repository, EventIntegrityChecker checker, LifecycleEvidence lifecycle,
            ReadSnapshot snapshots, QueryCursorCodec cursors, @Value("${audit.report.max-window:P31D}") Duration maxWindow,
            @Value("${audit.report.max-scanned:1000}") int scanLimit, @Value("${audit.report.max-concurrent:2}") int concurrent) {
        if (maxWindow.isNegative() || maxWindow.isZero() || maxWindow.getNano() != 0 || maxWindow.compareTo(Duration.ofDays(366)) > 0
                || scanLimit < 1 || scanLimit > 10000 || concurrent < 1 || concurrent > 4) {
            throw new IllegalArgumentException("Invalid report time, scan or concurrency bounds");
        }
        this.repository = repository;
        this.checker = checker;
        this.lifecycle = lifecycle;
        this.snapshots = snapshots;
        this.cursors = cursors;
        this.maxWindow = maxWindow;
        this.scanLimit = scanLimit;
        this.permits = new Semaphore(concurrent);
    }

    public Report report(AccessReportQuery query) {
        return report(query, ResourceScope.internal());
    }

    public Report report(AccessReportQuery query, ResourceScope scope) {
        if (Duration.between(query.from(), query.to()).compareTo(maxWindow) > 0) {
            throw new InvalidEventException("Report time window exceeds configured maximum");
        }
        if (!permits.tryAcquire()) {
            throw new AuditQueryException(429, "Report capacity exhausted; retry later");
        }
        try {
            return snapshots.execute(30, () -> page(query, scope));
        } finally {
            permits.release();
        }
    }

    private Report page(AccessReportQuery query, ResourceScope scope) {
        Position position;
        if (query.cursor() == null) {
            var head = repository.head().orElseThrow(() -> new AuditQueryException(503, "Report snapshot unavailable"));
            if (head.sequence() < 0 || !head.hash().matches("[0-9a-f]{64}")) {
                throw new AuditQueryException(503, "Report snapshot is invalid");
            }
            position = new Position(0, head.sequence(), head.hash());
        } else {
            position = cursors.decode(query.cursor(), DOMAIN, scope.context(query.context()));
        }
        if (position.upper() == 0 && !position.headHash().equals("0".repeat(64))
                || position.upper() > 0 && !repository.hashAt(position.upper()).orElse("").equals(position.headHash())) {
            throw new AuditQueryException(409, "Report pagination snapshot is no longer available");
        }
        List<Entry> entries = new ArrayList<>();
        int scanned = 0;
        int excluded = 0;
        boolean more = false;
        try (var stream = repository.accessCandidates(query, position.after(), position.upper(), scanLimit, scope)) {
            var rows = stream.iterator();
            while (rows.hasNext()) {
                var event = rows.next();
                if (++scanned > scanLimit) {
                    throw new AuditQueryException(413, "Report scan budget exceeded; narrow the time, account or action filters");
                }
                var payload = checker.check(event);
                Object value = payload.get("outcome");
                if (!(value instanceof String outcome) || !AccessReportQuery.OUTCOMES.contains(outcome)) {
                    excluded++;
                    continue;
                }
                if (query.outcome() != null && !query.outcome().equals(outcome)) {
                    continue;
                }
                boolean archived = lifecycle.archived(event.eventId());
                if (entries.size() == query.limit()) {
                    more = true;
                    break;
                }
                entries.add(new Entry(event.eventId(), event.sequence(), event.actorId(), event.resourceId(), event.eventType(),
                        outcome, event.timestamp(), archived));
            }
        } catch (EventIntegrityException exception) {
            throw new AuditQueryException(503, "Report source failed integrity checks");
        }
        String cursor = more ? cursors.encode(new Position(entries.getLast().sequence(), position.upper(), position.headHash()),
                DOMAIN, scope.context(query.context())) : null;
        return new Report(AccessReportQuery.VERSION, List.copyOf(entries), query.limit(), cursor,
                new ChainHead(position.upper(), position.headHash()), scanned, excluded);
    }
}
