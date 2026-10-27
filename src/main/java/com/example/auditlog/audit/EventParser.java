package com.example.auditlog.audit;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EventParser {

    public static final int MAX_BYTES = 256 * 1024;
    private static final Set<String> FIELDS = Set.of(
            "eventType", "actorId", "resourceType", "resourceId", "payload", "timestamp");
    private final CanonicalJson canonical;
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(24).maxNumberLength(128).maxStringLength(MAX_BYTES).build())
                    .build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public EventParser(CanonicalJson canonical) {
        this.canonical = canonical;
    }

    @SuppressWarnings("unchecked")
    public EventInput parse(byte[] body) {
        if (body == null || body.length == 0) {
            throw new InvalidEventException("Event body is required");
        }
        if (body.length > MAX_BYTES) {
            throw new InvalidEventException("Event body exceeds 256 KiB", 413);
        }
        Object decoded;
        try {
            decoded = mapper.readValue(body, Object.class);
        } catch (RuntimeException exception) {
            throw new InvalidEventException("Malformed JSON or parser limit exceeded");
        }
        if (!(decoded instanceof Map<?, ?> root) || !FIELDS.containsAll(root.keySet())) {
            throw new InvalidEventException("Event must be an object with supported fields only");
        }
        if (!(root.get("payload") instanceof Map<?, ?> payload)) {
            throw new InvalidEventException("payload must be a JSON object");
        }
        validatePayload(payload, 0);
        canonical.bytes(root); // Validate every string/number, including values later encrypted.
        Instant timestamp = null;
        if (root.containsKey("timestamp")) {
            if (!(root.get("timestamp") instanceof String supplied)) {
                throw new InvalidEventException("timestamp must be an ISO-8601 instant");
            }
            try {
                timestamp = Instant.parse(supplied);
            } catch (DateTimeParseException exception) {
                throw new InvalidEventException("timestamp must be an ISO-8601 instant");
            }
            if (timestamp.getNano() % 1000 != 0 || timestamp.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                    || timestamp.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))) {
                throw new InvalidEventException("timestamp must have microsecond precision and a year from 0001 to 9999");
            }
        }
        return new EventInput(text(root, "eventType"), text(root, "actorId"), text(root, "resourceType"),
                text(root, "resourceId"), (Map<String, Object>) payload, timestamp);
    }

    private String text(Map<?, ?> root, String field) {
        if (!(root.get(field) instanceof String value) || value.isBlank() || value.length() > 255) {
            throw new InvalidEventException(field + " must be nonblank and at most 255 UTF-16 units");
        }
        return value;
    }

    private void validatePayload(Object value, int depth) {
        if (depth > 20) {
            throw new InvalidEventException("payload depth exceeds 20");
        }
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("$auditProtected")) {
                throw new InvalidEventException("payload uses a reserved protection marker");
            }
            map.values().forEach(item -> validatePayload(item, depth + 1));
        } else if (value instanceof java.util.List<?> list) {
            list.forEach(item -> validatePayload(item, depth + 1));
        }
    }
}
