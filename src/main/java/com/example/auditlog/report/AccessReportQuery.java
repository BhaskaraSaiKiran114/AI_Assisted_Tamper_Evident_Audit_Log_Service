package com.example.auditlog.report;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.auditlog.audit.EventQuery;
import com.example.auditlog.audit.InvalidEventException;

public record AccessReportQuery(String accountId, String actorId, String action, String outcome, Instant from,
        Instant to, int limit, String cursor, boolean includeArchived) {

    public static final String VERSION = "access-report-v1";
    public static final Set<String> ACTIONS = Set.of("READ", "UPDATE", "EXPORT");
    public static final Set<String> OUTCOMES = Set.of("SUCCESS", "DENIED", "FAILURE");

    public AccessReportQuery {
        new EventQuery(actorId, "CLIENT_ACCOUNT", accountId, action, from, to, limit, cursor, includeArchived);
        if (from == null || to == null) {
            throw new InvalidEventException("Report requires from and to occurrence-time bounds");
        }
        if (action != null && !ACTIONS.contains(action) || outcome != null && !OUTCOMES.contains(outcome)) {
            throw new InvalidEventException("Unsupported report action or outcome");
        }
    }

    public static AccessReportQuery parse(Map<String, List<String>> parameters) {
        if (!Set.of("accountId", "actorId", "action", "outcome", "from", "to", "limit", "cursor", "includeArchived")
                .containsAll(parameters.keySet()) || parameters.values().stream().anyMatch(value -> value.size() != 1)) {
            throw new InvalidEventException("Unknown or repeated report parameter");
        }
        try {
            return new AccessReportQuery(value(parameters, "accountId"), value(parameters, "actorId"), value(parameters, "action"),
                    value(parameters, "outcome"), time(value(parameters, "from")), time(value(parameters, "to")),
                    parameters.containsKey("limit") ? Integer.parseInt(value(parameters, "limit")) : 50,
                    value(parameters, "cursor"), EventQuery.archived(parameters));
        } catch (NumberFormatException | DateTimeParseException exception) {
            throw new InvalidEventException("Invalid report limit or ISO-8601 time bound");
        }
    }

    public Map<String, Object> context() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("reportVersion", VERSION);
        fields.put("accountId", accountId);
        fields.put("actorId", actorId);
        fields.put("action", action);
        fields.put("outcome", outcome);
        fields.put("from", from.toString());
        fields.put("to", to.toString());
        fields.put("limit", limit);
        fields.put("includeArchived", includeArchived);
        return fields;
    }

    private static String value(Map<String, List<String>> values, String key) {
        return values.containsKey(key) ? values.get(key).getFirst() : null;
    }

    private static Instant time(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
