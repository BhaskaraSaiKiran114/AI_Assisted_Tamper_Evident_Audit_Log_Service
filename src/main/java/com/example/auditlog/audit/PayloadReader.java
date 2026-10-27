package com.example.auditlog.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.auditlog.persistence.AuditReadRepository;
import org.springframework.stereotype.Component;

@Component
public class PayloadReader {

    private final AuditReadRepository repository;
    private final PayloadProtector protector;
    private final StoredJson json;

    public PayloadReader(AuditReadRepository repository, PayloadProtector protector, StoredJson json) {
        this.repository = repository;
        this.protector = protector;
        this.json = json;
    }

    public Object read(UUID eventId, Map<String, Object> payload) {
        Map<String, String> keys = repository.wrappedKeys(eventId);
        try {
            return visit(eventId, payload, "", keys);
        } catch (RuntimeException exception) {
            throw new AuditQueryException(503, "Protected payload is unavailable");
        }
    }

    private Object visit(UUID id, Object value, String path, Map<String, String> keys) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("$auditProtected")) {
                if (map.size() != 1 || !(map.get("$auditProtected") instanceof Map<?, ?> envelope)
                        || !envelope.keySet().equals(java.util.Set.of("version", "algorithm", "nonce", "ciphertext"))
                        || !"1".equals(String.valueOf(envelope.get("version")))
                        || !"AES-256-GCM".equals(envelope.get("algorithm")) || !keys.containsKey(path)) {
                    throw new IllegalStateException("Invalid or unavailable protected field");
                }
                return json.value(protector.decrypt(id, path, envelope, keys.get(path)));
            }
            Map<String, Object> decoded = new LinkedHashMap<>();
            map.forEach((key, item) -> decoded.put((String) key,
                    visit(id, item, path + "/" + ((String) key).replace("~", "~0").replace("/", "~1"), keys)));
            return decoded;
        }
        if (value instanceof List<?> list) {
            List<Object> decoded = new ArrayList<>();
            for (int index = 0; index < list.size(); index++) {
                decoded.add(visit(id, list.get(index), path + "/" + index, keys));
            }
            return decoded;
        }
        return value;
    }
}
