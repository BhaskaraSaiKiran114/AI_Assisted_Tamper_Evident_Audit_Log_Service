package com.example.auditlog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CanonicalJsonTest {

    private final CanonicalJson canonical = new CanonicalJson();

    @Test
    void goldenEncodingNormalizesNumbersAndEscapesControlsWithoutChangingArrayOrder() {
        byte[] bytes = canonical.bytes(Map.of("z", List.of(new BigDecimal("1.00"), new BigDecimal("-0.0")),
                "a", "quote\"\n\\é"));
        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .isEqualTo("{\"a\":\"quote\\\"\\u000a\\\\é\",\"z\":[1,0]}");
        assertThat(canonical.bytes(Map.of("n", new BigDecimal("1e2"))))
                .isEqualTo(canonical.bytes(Map.of("n", 100)));
        assertThat(canonical.bytes(List.of(1, 2))).isNotEqualTo(canonical.bytes(List.of(2, 1)));
    }

    @Test
    void fixedHashVectorCommitsTheDomainAndBigEndianByteLength() {
        // Expected hash calculated independently with Python hashlib, not this implementation.
        assertThat(canonical.hash("{}".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("41543cbd1c77d0846505e8c10b91960233f20f4906c0e37c9aac741c11ff0365");
    }

    @Test
    void rejectsAmbiguousUnicodeFloatingPointAndUnboundedExponents() {
        assertThatThrownBy(() -> canonical.bytes("\ud800")).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> canonical.bytes(Double.NaN)).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> canonical.bytes(new BigDecimal("1e999999")))
                .isInstanceOf(InvalidEventException.class);
    }
}
