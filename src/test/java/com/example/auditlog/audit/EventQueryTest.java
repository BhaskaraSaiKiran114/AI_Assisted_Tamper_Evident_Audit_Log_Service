package com.example.auditlog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EventQueryTest {

    @Test
    void defaultsAndEquivalentInstantFiltersAreNormalized() {
        assertThat(EventQuery.parse(Map.of()).limit()).isEqualTo(50);
        assertThat(EventQuery.parse(Map.of("from", List.of("2020-01-01T01:00:00+01:00"))).from())
                .isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
    }

    @Test
    void rejectsUnknownRepeatedBlankUnboundedAndMalformedFilters() {
        List<Map<String, List<String>>> invalid = List.of(
                Map.of("unknown", List.of("x")), Map.of("actorId", List.of("a", "b")),
                Map.of("actorId", List.of(" ")), Map.of("resourceId", List.of("x".repeat(256))),
                Map.of("limit", List.of("0")), Map.of("limit", List.of("201")), Map.of("limit", List.of("bad")),
                Map.of("from", List.of("bad")), Map.of("from", List.of("2020-01-01T00:00:00.000000001Z")),
                Map.of("from", List.of("2020-01-01T00:00:00Z"), "to", List.of("2020-01-01T00:00:00Z")),
                Map.of("cursor", List.of("")));
        for (var parameters : invalid) {
            assertThatThrownBy(() -> EventQuery.parse(parameters)).isInstanceOf(InvalidEventException.class);
        }
    }
}
