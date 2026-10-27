package com.example.auditlog.audit;

import static com.example.auditlog.audit.VerificationResult.ViolationType.CONTENT_HASH_MISMATCH;
import static com.example.auditlog.audit.VerificationResult.ViolationType.INDEX_FIELD_MISMATCH;
import static com.example.auditlog.audit.VerificationResult.ViolationType.MALFORMED_CONTENT;
import static com.example.auditlog.audit.VerificationResult.ViolationType.UNSUPPORTED_SCHEMA_VERSION;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.Map;

import com.example.auditlog.persistence.StoredEvent;
import org.springframework.stereotype.Component;

@Component
public class EventIntegrityChecker {

    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();
    private final CanonicalJson canonical;
    private final StoredJson json;

    public EventIntegrityChecker(CanonicalJson canonical, StoredJson json) {
        this.canonical = canonical;
        this.json = json;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> check(StoredEvent event) {
        if (event.schemaVersion() != 1) {
            throw new EventIntegrityException(UNSUPPORTED_SCHEMA_VERSION);
        }
        if (!canonical.hash(event.canonicalBytes()).equals(event.recordHash())) {
            throw new EventIntegrityException(CONTENT_HASH_MISMATCH);
        }
        try {
            Map<String, Object> envelope = json.object(event.canonicalBytes());
            if (!"1".equals(String.valueOf(envelope.get("schemaVersion")))) {
                throw new EventIntegrityException(UNSUPPORTED_SCHEMA_VERSION);
            }
            if (!(envelope.get("payload") instanceof Map<?, ?> payload)
                    || !Arrays.equals(canonical.bytes(envelope), event.canonicalBytes())) {
                throw new EventIntegrityException(MALFORMED_CONTENT);
            }
            Object storedPayload = json.object(event.payloadJson().getBytes(StandardCharsets.UTF_8));
            byte[] expected = canonical.bytes(Map.ofEntries(
                    Map.entry("schemaVersion", event.schemaVersion()), Map.entry("eventId", event.eventId().toString()),
                    Map.entry("sequence", event.sequence()), Map.entry("eventType", event.eventType()),
                    Map.entry("actorId", event.actorId()), Map.entry("resourceType", event.resourceType()),
                    Map.entry("resourceId", event.resourceId()), Map.entry("timestamp", TIME.format(event.timestamp())),
                    Map.entry("ingestedAt", TIME.format(event.ingestedAt())), Map.entry("payload", storedPayload),
                    Map.entry("previousHash", event.previousHash())));
            if (!Arrays.equals(expected, event.canonicalBytes())) {
                throw new EventIntegrityException(INDEX_FIELD_MISMATCH);
            }
            return (Map<String, Object>) payload;
        } catch (EventIntegrityException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EventIntegrityException(MALFORMED_CONTENT);
        }
    }
}
