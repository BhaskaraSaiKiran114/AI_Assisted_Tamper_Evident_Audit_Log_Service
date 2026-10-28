package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import com.example.auditlog.security.ResourceScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ResourceScopeTest {

    @Test
    void invalidAssignmentShapesAndAmbiguousGlobalClaimsAreRejected() {
        for (Object value : List.of("true", 1, List.of())) {
            assertThatThrownBy(() -> ResourceScope.claims("a", Map.of("audit_global", value)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (Object value : List.of("*", java.util.Collections.nCopies(101, Map.of()), List.of(1),
                List.of(Map.of("resourceType", "A", "resourceId", "id", "extra", "x")),
                List.of(Map.of("resourceType", 1, "resourceId", "id")),
                List.of(Map.of("resourceType", "A", "resourceId", 1)),
                List.of(Map.of("resourceType", "A", "resourceId", "id\n")),
                List.of(Map.of("resourceType", "A", "resourceId", "x".repeat(256))),
                List.of(Map.of("resourceType", " ", "resourceId", "id")))) {
            assertThatThrownBy(() -> ResourceScope.claims("a", Map.of("audit_resources", value)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> ResourceScope.claims("a", Map.of("audit_global", true, "audit_resources",
                List.of(Map.of("resourceType", "A", "resourceId", "id")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignmentOrderingAndDuplicatesDoNotChangeCursorContextAndWildcardsAreLiteral() {
        var first = Map.of("resourceType", "A", "resourceId", "1");
        var second = Map.of("resourceType", "A", "resourceId", "2");
        var a = ResourceScope.claims("a", Map.of("audit_resources", List.of(first, second, first)));
        var b = ResourceScope.claims("a", Map.of("audit_resources", List.of(second, first)));
        assertThat(a.context(Map.of())).isEqualTo(b.context(Map.of()));
        assertThat(a.permits("A", "1")).isTrue();
        assertThat(a.permits("B", "1")).isFalse();
        assertThatThrownBy(() -> a.require("A", "3")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(a::requireGlobal).isInstanceOf(AccessDeniedException.class);
        a.require("A", "2");
        var literal = ResourceScope.claims("a", Map.of("audit_resources", List.of(Map.of("resourceType", "*", "resourceId", "*"))));
        assertThat(literal.permits("A", "1")).isFalse();
        ResourceScope.internal().requireGlobal();
        ResourceScope.internal().require("any", "any");
    }

    @Test
    void absentUnauthenticatedOrMalformedContextsCannotBecomeGlobal() {
        assertThatThrownBy(() -> ResourceScope.from(null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ResourceScope.from(new TestingAuthenticationToken("a", "b")))
                .isInstanceOf(AccessDeniedException.class);
        for (Map<String, Object> claims : List.of(Map.<String, Object>of("sub", "a"), Map.<String, Object>of("audit_global", "true"))) {
            var token = new JwtAuthenticationToken(Jwt.withTokenValue("synthetic").header("alg", "RS256").claims(c -> c.putAll(claims)).build(),
                    List.of());
            assertThatThrownBy(() -> ResourceScope.from(token)).isInstanceOf(AccessDeniedException.class);
        }
        var token = new JwtAuthenticationToken(Jwt.withTokenValue("synthetic").header("alg", "RS256").subject("a")
                .claim("audit_global", true).build());
        assertThatThrownBy(() -> ResourceScope.from(token)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void emptyScopeAddsDenyPredicateAndAssignmentsAreBoundParameters() {
        var sql = new StringBuilder("SELECT * FROM audit_event WHERE 1=1");
        var parameters = new java.util.ArrayList<>();
        ResourceScope.claims("a", Map.of()).constrain(sql, parameters);
        assertThat(sql.toString()).endsWith(" AND (1 = 0)");
        ResourceScope.claims("a", Map.of("audit_resources", List.of(Map.of("resourceType", "A", "resourceId", "' OR 1=1 --"))))
                .constrain(sql, parameters);
        assertThat(sql.toString()).doesNotContain("' OR 1=1 --");
        assertThat(parameters).containsExactly("A", "' OR 1=1 --");
    }
}
