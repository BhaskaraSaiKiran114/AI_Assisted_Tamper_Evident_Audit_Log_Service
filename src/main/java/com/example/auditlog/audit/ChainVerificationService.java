package com.example.auditlog.audit;

import static com.example.auditlog.audit.VerificationResult.ViolationType.HEAD_MISMATCH;
import static com.example.auditlog.audit.VerificationResult.ViolationType.PREVIOUS_HASH_MISMATCH;
import static com.example.auditlog.audit.VerificationResult.ViolationType.SEQUENCE_GAP;

import java.util.UUID;
import java.util.concurrent.Semaphore;

import com.example.auditlog.audit.VerificationResult.ChainHead;
import com.example.auditlog.audit.VerificationResult.Inconsistency;
import com.example.auditlog.audit.VerificationResult.ViolationType;
import com.example.auditlog.persistence.AuditReadRepository;
import com.example.auditlog.persistence.StoredEvent;
import com.example.auditlog.persistence.ReadSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class ChainVerificationService {

    private final AuditReadRepository repository;
    private final EventIntegrityChecker checker;
    private final ReadSnapshot snapshots;
    private final Semaphore permits;

    public ChainVerificationService(AuditReadRepository repository, EventIntegrityChecker checker, ReadSnapshot snapshots,
            @Value("${audit.verification.max-concurrent:2}") int maxConcurrent) {
        this.repository = repository;
        this.checker = checker;
        this.snapshots = snapshots;
        if (maxConcurrent < 1 || maxConcurrent > 16) {
            throw new IllegalArgumentException("Verification concurrency must be from 1 to 16");
        }
        permits = new Semaphore(maxConcurrent);
    }

    public VerificationResult verify() {
        if (!permits.tryAcquire()) {
            throw new AuditQueryException(429, "Verification capacity exhausted; retry later");
        }
        try {
            return snapshots.execute(60, this::verifySnapshot);
        } finally {
            permits.release();
        }
    }

    private VerificationResult verifySnapshot() {
        ChainHead head = repository.head().orElse(null);
        if (head == null || head.sequence() < 0 || !head.hash().matches("[0-9a-f]{64}")
                || head.sequence() == 0 && !head.hash().equals("0".repeat(64))) {
            return broken(0, head, 0, null, HEAD_MISMATCH);
        }
        long count = 0;
        long lastSequence = 0;
        UUID lastId = null;
        String previous = "0".repeat(64);
        try (var stream = repository.streamAll()) {
            var rows = stream.iterator();
            while (rows.hasNext()) {
                StoredEvent event = rows.next();
                count++;
                if (event.sequence() != lastSequence + 1) {
                    return broken(count, head, lastSequence + 1, null, SEQUENCE_GAP);
                }
                if (event.sequence() > head.sequence()) {
                    return broken(count, head, event.sequence(), event.eventId(), HEAD_MISMATCH);
                }
                try {
                    checker.check(event);
                } catch (EventIntegrityException exception) {
                    return broken(count, head, event.sequence(), event.eventId(), exception.type());
                }
                if (!event.previousHash().equals(previous)) {
                    return broken(count, head, event.sequence(), event.eventId(), PREVIOUS_HASH_MISMATCH);
                }
                lastSequence = event.sequence();
                lastId = event.eventId();
                previous = event.recordHash();
            }
        }
        if (lastSequence < head.sequence()) {
            return broken(count, head, lastSequence + 1, null, SEQUENCE_GAP);
        }
        if (!previous.equals(head.hash())) {
            return broken(count, head, lastSequence, lastId, HEAD_MISMATCH);
        }
        return new VerificationResult(true, count, head, null);
    }

    private VerificationResult broken(long count, ChainHead head, long sequence, UUID id, ViolationType type) {
        return new VerificationResult(false, count, head, new Inconsistency(sequence, id, type));
    }
}
