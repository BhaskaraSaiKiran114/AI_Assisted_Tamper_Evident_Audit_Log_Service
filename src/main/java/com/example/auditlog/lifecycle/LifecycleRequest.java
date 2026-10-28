package com.example.auditlog.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.audit.StoredJson;
import org.springframework.stereotype.Component;

@Component
public class LifecycleRequest {

    public record Retention(UUID operationId, String reason) { }
    public record Redaction(UUID operationId, String reason, String approvalReference, List<String> paths) { }

    private final StoredJson json;

    public LifecycleRequest(StoredJson json) {
        this.json = json;
    }

    public Retention retention(byte[] input) {
        var fields = fields(input, Set.of("operationId", "reason"));
        return new Retention(uuid(fields.get("operationId")), text(fields, "reason"));
    }

    public Redaction redaction(byte[] input) {
        var fields = fields(input, Set.of("operationId", "reason", "approvalReference", "confirmation", "paths"));
        if (!"ERASE_FIELD_KEYS".equals(fields.get("confirmation")) || !(fields.get("paths") instanceof List<?> list)
                || list.isEmpty() || list.size() > 20 || list.stream().anyMatch(value -> !(value instanceof String))) {
            throw new InvalidEventException("Redaction requires confirmation ERASE_FIELD_KEYS and 1-20 payload paths");
        }
        List<String> paths = list.stream().map(String.class::cast).sorted().toList();
        if (paths.stream().distinct().count() != paths.size()) {
            throw new InvalidEventException("Redaction paths must be unique");
        }
        for (String path : paths) {
            if (!path.startsWith("/") || path.length() > 1024
                    || java.util.regex.Pattern.compile("~(?![01])").matcher(path).find()) {
                throw new InvalidEventException("Redaction paths must be bounded RFC 6901 pointers");
            }
        }
        return new Redaction(uuid(fields.get("operationId")), text(fields, "reason"), text(fields, "approvalReference"), paths);
    }

    public static UUID uuid(Object value) {
        try {
            UUID id = UUID.fromString((String) value);
            if (!id.toString().equalsIgnoreCase((String) value)) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (RuntimeException exception) {
            throw new InvalidEventException("A valid UUID is required");
        }
    }

    private Map<String, Object> fields(byte[] bytes, Set<String> names) {
        if (bytes.length > 16 * 1024) {
            throw new InvalidEventException("Lifecycle request exceeds 16 KiB", 413);
        }
        try {
            var fields = json.object(bytes);
            if (!fields.keySet().equals(names)) {
                throw new IllegalArgumentException();
            }
            return fields;
        } catch (RuntimeException exception) {
            throw new InvalidEventException("Malformed lifecycle request or unsupported fields");
        }
    }

    private String text(Map<String, Object> fields, String name) {
        if (!(fields.get(name) instanceof String value) || value.isBlank() || value.length() > 255) {
            throw new InvalidEventException(name + " must be nonblank and at most 255 UTF-16 units");
        }
        return value;
    }
}
