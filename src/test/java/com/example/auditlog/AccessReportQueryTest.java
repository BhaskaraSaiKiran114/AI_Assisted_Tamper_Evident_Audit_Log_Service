package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.report.AccessReportQuery;
import com.example.auditlog.report.AccessReportService;
import org.junit.jupiter.api.Test;

class AccessReportQueryTest {

    @Test
    void requiresBothBoundsAndRejectsUnknownRepeatedAndUnboundedInput() {
        for (var request : List.of(Map.<String, List<String>>of(), Map.of("from", List.of("2026-10-01T00:00:00Z")),
                changed("payload", "true"), changed("from", "2026-10-01T00:00:00Z", "2026-10-01T01:00:00Z"),
                changed("accountId", " "), changed("actorId", "x".repeat(256)), changed("cursor", "x".repeat(2049)))) {
            assertThatThrownBy(() -> AccessReportQuery.parse(request)).isInstanceOf(InvalidEventException.class);
        }
    }

    @Test
    void rejectsUnsupportedVocabularyPaginationArchiveAndTimePrecision() {
        for (var request : List.of(changed("action", "read"), changed("action", "DELETE"), changed("outcome", "UNKNOWN"),
                changed("outcome", "success"), changed("limit", "0"), changed("limit", "201"), changed("limit", "bad"),
                changed("includeArchived", "TRUE"), changed("from", "2026-10-01"),
                changed("from", "2026-10-01T00:00:00.0000001Z"), changed("to", "2026-10-01T00:00:00Z"))) {
            assertThatThrownBy(() -> AccessReportQuery.parse(request)).isInstanceOf(InvalidEventException.class);
        }
    }

    @Test
    void equivalentTimeOffsetsShareCursorContextAndCursorTextIsNotPartOfFingerprint() {
        var first = AccessReportQuery.parse(base());
        var other = changed("from", "2026-10-01T05:30:00+05:30");
        other.put("cursor", List.of("opaque"));
        assertThat(AccessReportQuery.parse(other).context()).isEqualTo(first.context());
        assertThat(first.limit()).isEqualTo(50);
        assertThat(first.includeArchived()).isFalse();
    }

    @Test
    void acceptsOnlyExplicitActionOutcomeAndLiteralArchiveMode() {
        var values = changed("action", "EXPORT");
        values.put("outcome", List.of("DENIED"));
        values.put("includeArchived", List.of("true"));
        values.put("limit", List.of("200"));
        assertThat(AccessReportQuery.parse(values).action()).isEqualTo("EXPORT");
        assertThat(AccessReportQuery.parse(values).outcome()).isEqualTo("DENIED");
        assertThat(AccessReportQuery.parse(values).includeArchived()).isTrue();
    }

    @Test
    void resourceConfigurationFailsClosedOutsideSupportedBounds() {
        for (Duration window : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofNanos(1), Duration.ofDays(367))) {
            assertThatThrownBy(() -> service(window, 1000, 2)).isInstanceOf(IllegalArgumentException.class);
        }
        for (int scan : new int[] {0, 10001}) {
            assertThatThrownBy(() -> service(Duration.ofDays(31), scan, 2)).isInstanceOf(IllegalArgumentException.class);
        }
        for (int concurrent : new int[] {0, 5}) {
            assertThatThrownBy(() -> service(Duration.ofDays(31), 1000, concurrent)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private AccessReportService service(Duration window, int scan, int concurrent) {
        return new AccessReportService(null, null, null, null, null, window, scan, concurrent);
    }

    private Map<String, List<String>> base() {
        return new LinkedHashMap<>(Map.of("from", List.of("2026-10-01T00:00:00Z"), "to", List.of("2026-10-02T00:00:00Z")));
    }

    private Map<String, List<String>> changed(String key, String... values) {
        var result = base();
        result.put(key, List.of(values));
        return result;
    }
}
