package com.example.auditlog.audit;

import static com.example.auditlog.audit.VerificationResult.ViolationType.HEAD_MISMATCH;
import static com.example.auditlog.audit.VerificationResult.ViolationType.PREVIOUS_HASH_MISMATCH;
import static com.example.auditlog.audit.VerificationResult.ViolationType.SEQUENCE_GAP;

import java.util.UUID;

import com.example.auditlog.audit.VerificationResult.ChainHead;
import com.example.auditlog.audit.VerificationResult.Inconsistency;
import com.example.auditlog.audit.VerificationResult.ViolationType;
import com.example.auditlog.persistence.AuditReadRepository;
import com.example.auditlog.persistence.StoredEvent;
import com.example.auditlog.persistence.ReadSnapshot;
import org.springframework.stereotype.Service;

@Service
public class ChainVerificationService {

    private final AuditReadRepository repository;
    private final EventIntegrityChecker checker;
    private final ReadSnapshot snapshots;

    public ChainVerificationService(AuditReadRepository repository, EventIntegrityChecker checker, ReadSnapshot snapshots) {
        this.repository = repository;
        this.checker = checker;
        this.snapshots = snapshots;
    }

    public VerificationResult verify() {
        return snapshots.execute(60, this::verifySnapshot);
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
