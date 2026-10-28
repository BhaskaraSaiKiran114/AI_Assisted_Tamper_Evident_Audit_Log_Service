package com.example.auditlog.export;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import com.example.auditlog.audit.AuditQueryException;
import com.example.auditlog.audit.EventIntegrityChecker;
import com.example.auditlog.export.ExportBundle.Header;
import com.example.auditlog.lifecycle.LifecycleEvidence;
import com.example.auditlog.persistence.AuditReadRepository;
import com.example.auditlog.persistence.ReadSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExportService {

    private final AuditReadRepository repository;
    private final EventIntegrityChecker checker;
    private final LifecycleEvidence lifecycle;
    private final ReadSnapshot snapshots;
    private final ExportSigner signer;
    private final Clock clock;
    private final Semaphore permits;
    private final int maxRecords;
    private final int maxSelected;
    private final int maxBytes;

    public ExportService(AuditReadRepository repository, EventIntegrityChecker checker, LifecycleEvidence lifecycle,
            ReadSnapshot snapshots, ExportSigner signer, Clock clock,
            @Value("${audit.export.max-concurrent:2}") int concurrent,
            @Value("${audit.export.max-records:10000}") int maxRecords,
            @Value("${audit.export.max-selected:1000}") int maxSelected,
            @Value("${audit.export.max-bytes:16777216}") int maxBytes) {
        if (concurrent < 1 || concurrent > 4 || maxRecords < 1 || maxRecords > ExportBundle.MAX_RECORDS
                || maxSelected < 1 || maxSelected > ExportBundle.MAX_SELECTED || maxBytes < 1024 || maxBytes > ExportBundle.MAX_BODY) {
            throw new IllegalArgumentException("Invalid export resource limits");
        }
        this.repository = repository;
        this.checker = checker;
        this.lifecycle = lifecycle;
        this.snapshots = snapshots;
        this.signer = signer;
        this.clock = clock;
        this.permits = new Semaphore(concurrent);
        this.maxRecords = maxRecords;
        this.maxSelected = maxSelected;
        this.maxBytes = maxBytes;
    }

    public byte[] export(ExportFilter filter) {
        signer.privateKey();
        if (!permits.tryAcquire()) {
            throw new AuditQueryException(429, "Export capacity exhausted; retry later");
        }
        try {
            return snapshots.execute(60, () -> build(filter));
        } finally {
            permits.release();
        }
    }

    private byte[] build(ExportFilter filter) {
        var head = repository.head().orElseThrow(this::broken);
        if (head.sequence() < 0 || !head.hash().matches("[0-9a-f]{64}")) {
            throw broken();
        }
        if (head.sequence() > maxRecords) {
            throw tooLarge();
        }
        List<Header> headers = new ArrayList<>();
        String previous = "0".repeat(64);
        int selected = 0;
        long size = 8;
        try (var stream = repository.streamAll()) {
            var rows = stream.iterator();
            while (rows.hasNext()) {
                var event = rows.next();
                if (event.sequence() != headers.size() + 1L || event.sequence() > head.sequence()
                        || !event.previousHash().equals(previous)) {
                    throw broken();
                }
                checker.check(event);
                lifecycle.checkEvent(event);
                boolean include = filter.matches(event);
                boolean archived = include && lifecycle.archived(event.eventId());
                include &= filter.includeArchived() || !archived;
                List<String> paths = include ? lifecycle.redactions(event.eventId()).keySet().stream().sorted().toList() : List.of();
                size += 73;
                if (include) {
                    selected++;
                    // Conservative modified-UTF bound includes UUID, path lengths, flags and content framing.
                    size += 47L + event.canonicalBytes().length;
                    for (String path : paths) {
                        size += 2L + 3L * path.length();
                    }
                }
                if (selected > maxSelected || size > maxBytes) {
                    throw tooLarge();
                }
                headers.add(new Header(event.sequence(), event.previousHash(), event.recordHash(),
                        include ? event.eventId() : null, include ? event.canonicalBytes() : null, archived, paths));
                previous = event.recordHash();
            }
        }
        if (headers.size() != head.sequence() || !previous.equals(head.hash())) {
            throw broken();
        }
        try {
            return ExportBundle.create(headers, filter.actorId(), filter.resourceId(), filter.resourceType(), filter.includeArchived(),
                    clock.instant(), head.sequence(), head.hash(), signer.privateKey(), signer.publicKey());
        } catch (IOException | GeneralSecurityException exception) {
            throw new AuditQueryException(503, "Export construction or signing failed");
        }
    }

    private AuditQueryException broken() {
        return new AuditQueryException(503, "Cannot export inconsistent audit evidence");
    }

    private AuditQueryException tooLarge() {
        return new AuditQueryException(413, "Export exceeds prototype proof, selection or byte limit");
    }
}
