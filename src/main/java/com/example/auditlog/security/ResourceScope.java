package com.example.auditlog.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Issuer-controlled assignments; request filters never grant access. */
public record ResourceScope(String subject, boolean global, List<Resource> resources) {

    public record Resource(String resourceType, String resourceId) { }

    public ResourceScope {
        resources = resources.stream().distinct().sorted(Comparator.comparing(Resource::resourceType)
                .thenComparing(Resource::resourceId)).toList();
    }

    /** Trusted in-process callers only; HTTP entry points must use from(authentication). */
    public static ResourceScope internal() {
        return new ResourceScope("trusted-in-process", true, List.of());
    }

    public static ResourceScope from(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            throw new AccessDeniedException("Resource authorization required");
        }
        ResourceScope scope;
        try {
            scope = claims(token.getName(), token.getToken().getClaims());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid resource authorization");
        }
        if (!scope.global() && scope.resources().isEmpty()) {
            throw new AccessDeniedException("Resource assignment required");
        }
        return scope;
    }

    public static ResourceScope claims(String subject, Map<String, Object> claims) {
        Object global = claims.get("audit_global");
        if (global != null && !(global instanceof Boolean)) {
            throw new IllegalArgumentException("audit_global must be boolean");
        }
        List<Resource> assignments = new ArrayList<>();
        Object raw = claims.get("audit_resources");
        if (raw != null) {
            if (!(raw instanceof List<?> list) || list.size() > 100) {
                throw new IllegalArgumentException("audit_resources must contain at most 100 assignments");
            }
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> entry) || !entry.keySet().equals(java.util.Set.of("resourceType", "resourceId"))
                        || !(entry.get("resourceType") instanceof String type) || !valid(type)
                        || !(entry.get("resourceId") instanceof String id) || !valid(id)) {
                    throw new IllegalArgumentException("Invalid resource assignment");
                }
                assignments.add(new Resource(type, id));
            }
        }
        if (Boolean.TRUE.equals(global) && !assignments.isEmpty()) {
            throw new IllegalArgumentException("Global and scoped assignments cannot be combined");
        }
        return new ResourceScope(subject, Boolean.TRUE.equals(global), assignments);
    }

    private static boolean valid(String value) {
        return !value.isBlank() && value.length() <= 255 && value.chars().noneMatch(Character::isISOControl);
    }

    public boolean permits(String type, String id) {
        return global || resources.contains(new Resource(type, id));
    }

    public void require(String type, String id) {
        if (!permits(type, id)) {
            throw new AccessDeniedException("Resource access denied");
        }
    }

    public void requireGlobal() {
        if (!global) {
            throw new AccessDeniedException("Global operator assignment required");
        }
    }

    public Map<String, Object> context(Map<String, Object> filters) {
        Map<String, Object> context = new LinkedHashMap<>(filters);
        context.put("authorization", Map.of("subject", subject, "global", global, "resources", resources.stream()
                .map(resource -> Map.of("resourceType", resource.resourceType(), "resourceId", resource.resourceId())).toList()));
        return context;
    }

    /** Parameterized predicate is applied before pagination, decryption and report counting. */
    public void constrain(StringBuilder sql, List<Object> parameters) {
        if (global) {
            return;
        }
        sql.append(" AND (1 = 0");
        for (Resource resource : resources) {
            sql.append(" OR (resource_type = ? AND resource_id = ?)");
            parameters.add(resource.resourceType());
            parameters.add(resource.resourceId());
        }
        sql.append(")");
    }
}
