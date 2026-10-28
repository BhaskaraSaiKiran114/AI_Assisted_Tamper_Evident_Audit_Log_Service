package com.example.auditlog.lifecycle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProtectedFields {

    private ProtectedFields() { }

    public static Map<String, Map<?, ?>> collect(Object payload) {
        Map<String, Map<?, ?>> fields = new LinkedHashMap<>();
        visit(payload, "", fields);
        return fields;
    }

    private static void visit(Object value, String path, Map<String, Map<?, ?>> fields) {
        if (value instanceof Map<?, ?> map) {
            if (map.get("$auditProtected") instanceof Map<?, ?> envelope) {
                fields.put(path, envelope);
                return;
            }
            map.forEach((key, item) -> visit(item, path + "/" + ((String) key).replace("~", "~0").replace("/", "~1"), fields));
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                visit(list.get(index), path + "/" + index, fields);
            }
        }
    }
}
