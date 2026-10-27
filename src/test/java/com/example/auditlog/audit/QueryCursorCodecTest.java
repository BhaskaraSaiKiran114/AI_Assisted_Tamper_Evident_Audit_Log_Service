package com.example.auditlog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import com.example.auditlog.audit.QueryCursorCodec.Position;
import org.junit.jupiter.api.Test;

class QueryCursorCodecTest {

    private final String key = Base64.getEncoder().encodeToString(new byte[32]); // Synthetic test key only.
    private final QueryCursorCodec codec = new QueryCursorCodec(new CanonicalJson(), new StoredJson(), key);
    private final EventQuery query = new EventQuery("actor", null, null, null, null, null, 2, null);

    @Test
    void roundTripsAcrossInstancesWithSharedKeyAndBindsAllFiltersAndLimit() {
        String cursor = codec.encode(new Position(2, 9, "a".repeat(64)), query);
        var restarted = new QueryCursorCodec(new CanonicalJson(), new StoredJson(), key);
        assertThat(restarted.decode(cursor, query)).isEqualTo(new Position(2, 9, "a".repeat(64)));
        assertThatThrownBy(() -> codec.decode(cursor,
                new EventQuery("other", null, null, null, null, null, 2, null)))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> codec.decode(cursor,
                new EventQuery("actor", null, null, null, null, null, 3, null)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void rejectsForgedMalformedAndWrongKeyCursors() {
        String cursor = codec.encode(new Position(2, 9, "a".repeat(64)), query);
        String forged = (cursor.charAt(0) == 'e' ? "f" : "e") + cursor.substring(1);
        for (String invalid : new String[] {forged, "garbage", "a.b", "x".repeat(2049)}) {
            assertThatThrownBy(() -> codec.decode(invalid, query)).isInstanceOf(InvalidEventException.class);
        }
        var otherKey = new QueryCursorCodec(new CanonicalJson(), new StoredJson(), "");
        assertThatThrownBy(() -> otherKey.decode(cursor, query)).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> new QueryCursorCodec(new CanonicalJson(), new StoredJson(), "bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
