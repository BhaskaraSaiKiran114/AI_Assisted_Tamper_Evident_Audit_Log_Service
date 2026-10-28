package com.example.auditlog.export;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.auditlog.audit.EventQuery;
import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.persistence.StoredEvent;

public record ExportFilter(String actorId, String resourceId, String resourceType, boolean includeArchived) {

    public ExportFilter {
        if (actorId == null && resourceId == null) {
            throw new InvalidEventException("Export requires actorId or resourceId");
        }
        if (resourceType != null && resourceId == null) {
            throw new InvalidEventException("resourceType requires resourceId");
        }
        for (String value : new String[] {actorId, resourceId, resourceType}) {
            if (value != null && (value.isBlank() || value.length() > 255)) {
                throw new InvalidEventException("Export filters must be nonblank and at most 255 UTF-16 units");
            }
        }
    }

    public static ExportFilter parse(Map<String, List<String>> parameters) {
        if (!Set.of("actorId", "resourceId", "resourceType", "includeArchived").containsAll(parameters.keySet())
                || parameters.values().stream().anyMatch(value -> value.size() != 1)) {
            throw new InvalidEventException("Unknown or repeated export parameter");
        }
        return new ExportFilter(value(parameters, "actorId"), value(parameters, "resourceId"),
                value(parameters, "resourceType"), EventQuery.archived(parameters));
    }

    public boolean matches(StoredEvent event) {
        return (actorId == null || actorId.equals(event.actorId()))
                && (resourceId == null || resourceId.equals(event.resourceId()))
                && (resourceType == null || resourceType.equals(event.resourceType()));
    }

    private static String value(Map<String, List<String>> parameters, String key) {
        return parameters.containsKey(key) ? parameters.get(key).getFirst() : null;
    }
}
