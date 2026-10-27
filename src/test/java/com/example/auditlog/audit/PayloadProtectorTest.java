package com.example.auditlog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PayloadProtectorTest {

    @Test
    void perFieldKeysAreWrappedAndCiphertextIsBoundToEventAndPointer() {
        PayloadProtector protector = new PayloadProtector(new CanonicalJson(), "", true, "/nested/a~1b");
        UUID id = UUID.randomUUID();
        Map<String, Object> original = Map.of("nested", Map.of("a/b", "synthetic-sensitive-value"));
        var first = protector.protect(id, original);
        var second = protector.protect(id, original);
        assertThat(first.payload()).isNotEqualTo(second.payload());
        assertThat(new String(new CanonicalJson().bytes(first.payload()), StandardCharsets.UTF_8))
                .doesNotContain("synthetic-sensitive-value");
        Map<?, ?> envelope = (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) first.payload()).get("nested"))
                .get("a/b");
        Map<?, ?> protectedValue = (Map<?, ?>) envelope.get("$auditProtected");
        String key = first.wrappedKeys().get("/nested/a~1b");
        assertThat(new String(protector.decrypt(id, "/nested/a~1b", protectedValue, key), StandardCharsets.UTF_8))
                .isEqualTo("\"synthetic-sensitive-value\"");
        assertThatThrownBy(() -> protector.decrypt(UUID.randomUUID(), "/nested/a~1b", protectedValue, key))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> protector.decrypt(id, "/wrong-path", protectedValue, key))
                .isInstanceOf(IllegalStateException.class);
        assertThat(original).isEqualTo(Map.of("nested", Map.of("a/b", "synthetic-sensitive-value")));
    }

    @Test
    void requiresExternalKeyOutsideEphemeralModeAndRejectsOverlappingPaths() {
        assertThatThrownBy(() -> new PayloadProtector(new CanonicalJson(), "", false, "/a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PayloadProtector(new CanonicalJson(), "", true, "/a,/a/b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PayloadProtector(new CanonicalJson(), "", true, "/a~2b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PayloadProtector(new CanonicalJson(), "", true, "/a\n~2b"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
