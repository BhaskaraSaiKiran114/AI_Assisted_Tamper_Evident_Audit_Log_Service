package com.example.auditlog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventParserTest {

    private final EventParser parser = new EventParser(new CanonicalJson());

    static String event(String payload) {
        return "{\"eventType\":\"USER_LOGIN\",\"actorId\":\"actor-1\",\"resourceType\":\"CLIENT_ACCOUNT\","
                + "\"resourceId\":\"resource-1\",\"payload\":" + payload + "}";
    }

    @Test
    void acceptsPreciseNumbersAndOptionalUtcTimestamp() {
        String json = event("{\"n\":9007199254740993.25}").replace("\"payload\":",
                "\"timestamp\":\"2026-10-28T01:00:00.123456+01:00\",\"payload\":");
        EventInput input = parser.parse(json.getBytes(StandardCharsets.UTF_8));
        assertThat(input.timestamp()).isEqualTo(Instant.parse("2026-10-28T00:00:00.123456Z"));
        assertThat(input.payload().get("n").toString()).isEqualTo("9007199254740993.25");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "1", "\"text\"", "{\"a\":1,\"a\":2}",
            "{\"$auditProtected\":{}}", "{\"n\":1e999999}", "{\"x\":NaN}"})
    void rejectsInvalidPayloads(String payload) {
        assertThatThrownBy(() -> parser.parse(event(payload).getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void rejectsMissingBlankOverlongUnknownAndDuplicateEnvelopeFieldsAndTrailingJson() {
        String valid = event("{}");
        for (String invalid : new String[] {"{}", valid.replace("actor-1", " "),
                valid.replace("actor-1", "a".repeat(256)), valid.replace("\"payload\":", "\"unknown\":1,\"payload\":"),
                valid.replace("\"payload\":", "\"actorId\":\"duplicate\",\"payload\":"), valid + " {}",
                valid.replace("\"payload\":", "\"timestamp\":null,\"payload\":"),
                valid.replace("\"payload\":", "\"timestamp\":\"2026-10-28T00:00:00.123456789Z\",\"payload\":")}) {
            assertThatThrownBy(() -> parser.parse(invalid.getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(InvalidEventException.class);
        }
    }

    @Test
    void rejectsOversizedBodiesAndDeepPayloads() {
        assertThatThrownBy(() -> parser.parse(new byte[EventParser.MAX_BYTES + 1]))
                .isInstanceOfSatisfying(InvalidEventException.class, failure -> assertThat(failure.status()).isEqualTo(413));
        String deep = "{\"x\":".repeat(22) + "0" + "}".repeat(22);
        assertThatThrownBy(() -> parser.parse(event(deep).getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidEventException.class);
    }
}
