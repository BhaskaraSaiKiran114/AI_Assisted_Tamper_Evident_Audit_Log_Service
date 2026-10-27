package com.example.auditlog.audit;

import java.util.Map;

import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Strict decoding for stored envelopes, cursors and decrypted values; never logs content. */
@Component
public class StoredJson {

    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
                            .maxNumberLength(128).maxStringLength(1024 * 1024).build()).build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public Object value(byte[] bytes) {
        // Ciphertext envelopes and exact decimal expansion can exceed the original request-byte cap.
        // Input requests/cursors are bounded separately; preserve legitimate committed records here.
        return mapper.readValue(bytes, Object.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> object(byte[] bytes) {
        Object value = value(bytes);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Stored JSON must be an object");
        }
        return (Map<String, Object>) value;
    }
}
