package com.example.auditlog.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.auditlog.persistence.AuditReadRepository;
import com.example.auditlog.lifecycle.LifecycleEvidence;
import com.example.auditlog.persistence.LifecycleRepository.Marker;
import org.springframework.stereotype.Component;

@Component
public class PayloadReader {

    public record ReadPayload(Object value, List<String> redactedPaths) { }

    private final AuditReadRepository repository;
    private final PayloadProtector protector;
    private final StoredJson json;
    private final LifecycleEvidence lifecycle;

    public PayloadReader(AuditReadRepository repository, PayloadProtector protector, StoredJson json, LifecycleEvidence lifecycle) {
        this.repository = repository;
        this.protector = protector;
        this.json = json;
        this.lifecycle = lifecycle;
    }

    public ReadPayload read(UUID eventId, Map<String, Object> payload) {
        Map<String, String> keys = repository.wrappedKeys(eventId);
        Map<String, Marker> redactions = lifecycle.redactions(eventId);
        try {
            return new ReadPayload(visit(eventId, payload, "", keys, redactions), redactions.keySet().stream().sorted().toList());
        } catch (RuntimeException exception) {
            throw new AuditQueryException(503, "Protected payload is unavailable");
        }
    }

    private Object visit(UUID id, Object value, String path, Map<String, String> keys, Map<String, Marker> redactions) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("$auditProtected")) {
                if (map.size() != 1 || !(map.get("$auditProtected") instanceof Map<?, ?> envelope)
                        || !envelope.keySet().equals(java.util.Set.of("version", "algorithm", "nonce", "ciphertext"))
                        || !"1".equals(String.valueOf(envelope.get("version")))
                        || !"AES-256-GCM".equals(envelope.get("algorithm"))) {
                    throw new IllegalStateException("Invalid or unavailable protected field");
                }
                if (redactions.containsKey(path)) {
                    Marker marker = redactions.get(path);
                    return Map.of("$auditRedacted", Map.of("operationId", marker.operationId().toString(),
                            "redactedAt", marker.at().toString()));
                }
                if (!keys.containsKey(path)) {
                    throw new IllegalStateException("Protected field has no key or redaction evidence");
                }
                return json.value(protector.decrypt(id, path, envelope, keys.get(path)));
            }
            Map<String, Object> decoded = new LinkedHashMap<>();
            map.forEach((key, item) -> decoded.put((String) key,
                    visit(id, item, path + "/" + ((String) key).replace("~", "~0").replace("/", "~1"), keys, redactions)));
            return decoded;
        }
        if (value instanceof List<?> list) {
            List<Object> decoded = new ArrayList<>();
            for (int index = 0; index < list.size(); index++) {
                decoded.add(visit(id, list.get(index), path + "/" + index, keys, redactions));
            }
            return decoded;
        }
        return value;
    }
}
