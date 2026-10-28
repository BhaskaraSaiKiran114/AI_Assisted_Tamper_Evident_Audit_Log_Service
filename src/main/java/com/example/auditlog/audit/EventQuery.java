package com.example.auditlog.audit;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record EventQuery(String actorId, String resourceType, String resourceId, String eventType,
        Instant from, Instant to, int limit, String cursor, boolean includeArchived) {

    public EventQuery(String actorId, String resourceType, String resourceId, String eventType,
            Instant from, Instant to, int limit, String cursor) {
        this(actorId, resourceType, resourceId, eventType, from, to, limit, cursor, false);
    }

    private static final Set<String> PARAMETERS = Set.of(
            "actorId", "resourceType", "resourceId", "eventType", "from", "to", "limit", "cursor", "includeArchived");

    public EventQuery {
        for (String value : new String[] {actorId, resourceType, resourceId, eventType}) {
            if (value != null && (value.isBlank() || value.length() > 255)) {
                throw new InvalidEventException("Filters must be nonblank and at most 255 UTF-16 units");
            }
        }
        if (limit < 1 || limit > 200) {
            throw new InvalidEventException("limit must be from 1 to 200");
        }
        for (Instant value : new Instant[] {from, to}) {
            if (value != null && (value.getNano() % 1000 != 0
                    || value.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                    || value.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z")))) {
                throw new InvalidEventException("Time filters require supported microsecond instants");
            }
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidEventException("from must be before to");
        }
        if (cursor != null && (cursor.isBlank() || cursor.length() > 2048)) {
            throw new InvalidEventException("Invalid pagination cursor");
        }
    }

    public static EventQuery parse(Map<String, List<String>> parameters) {
        if (!PARAMETERS.containsAll(parameters.keySet()) || parameters.values().stream().anyMatch(v -> v.size() != 1)) {
            throw new InvalidEventException("Unknown or repeated query parameter");
        }
        try {
            return new EventQuery(get(parameters, "actorId"), get(parameters, "resourceType"), get(parameters, "resourceId"),
                    get(parameters, "eventType"), time(get(parameters, "from")), time(get(parameters, "to")),
                    parameters.containsKey("limit") ? Integer.parseInt(get(parameters, "limit")) : 50,
                    get(parameters, "cursor"), archived(parameters));
        } catch (NumberFormatException | DateTimeParseException exception) {
            throw new InvalidEventException("Invalid limit or ISO-8601 time filter");
        }
    }

    public Map<String, Object> fingerprintFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("actorId", actorId);
        fields.put("resourceType", resourceType);
        fields.put("resourceId", resourceId);
        fields.put("eventType", eventType);
        fields.put("from", from == null ? null : from.toString());
        fields.put("to", to == null ? null : to.toString());
        fields.put("limit", limit);
        fields.put("includeArchived", includeArchived);
        return fields;
    }

    private static String get(Map<String, List<String>> parameters, String name) {
        return parameters.containsKey(name) ? parameters.get(name).getFirst() : null;
    }

    private static Instant time(String value) {
        return value == null ? null : Instant.parse(value);
    }

    public static boolean archived(Map<String, List<String>> parameters) {
        if (!parameters.containsKey("includeArchived")) {
            return false;
        }
        List<String> values = parameters.get("includeArchived");
        if (values.size() != 1 || !(values.getFirst().equals("true") || values.getFirst().equals("false"))) {
            throw new InvalidEventException("includeArchived must be true or false exactly once");
        }
        return values.getFirst().equals("true");
    }
}
