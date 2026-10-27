package com.example.auditlog.audit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

/** Version 1 encoding; deliberately independent of Jackson serializer settings. */
@Component
public class CanonicalJson {

    public byte[] bytes(Object value) {
        StringBuilder output = new StringBuilder();
        write(value, output, 0);
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    public String hash(byte[] canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("AUDIT-CHAIN-V1\0".getBytes(StandardCharsets.US_ASCII));
            digest.update(ByteBuffer.allocate(4).putInt(canonical.length).array());
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 unavailable", exception);
        }
    }

    private void write(Object value, StringBuilder out, int depth) {
        if (depth > 32) {
            throw new InvalidEventException("JSON nesting exceeds encoding limit");
        }
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            string(string, out);
        } else if (value instanceof Boolean bool) {
            out.append(bool);
        } else if (value instanceof Number number) {
            BigDecimal decimal;
            if (number instanceof BigDecimal bigDecimal) {
                decimal = bigDecimal;
            } else if (number instanceof BigInteger bigInteger) {
                decimal = new BigDecimal(bigInteger);
            } else if (number instanceof Integer || number instanceof Long) {
                decimal = BigDecimal.valueOf(number.longValue());
            } else {
                throw new InvalidEventException("Unsupported JSON number representation");
            }
            decimal = decimal.stripTrailingZeros();
            if (decimal.precision() > 128 || Math.abs((long) decimal.scale()) > 128) {
                throw new InvalidEventException("JSON number exceeds precision or exponent limit");
            }
            out.append(decimal.signum() == 0 ? "0" : decimal.toPlainString());
        } else if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String)) {
                    throw new InvalidEventException("JSON object keys must be strings");
                }
                sorted.put((String) key, item);
            });
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                string(entry.getKey(), out);
                out.append(':');
                write(entry.getValue(), out, depth + 1);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    out.append(',');
                }
                write(list.get(index), out, depth + 1);
            }
            out.append(']');
        } else {
            throw new InvalidEventException("Unsupported JSON value");
        }
    }

    private void string(String value, StringBuilder out) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    } else if (Character.isHighSurrogate(character)) {
                        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                            throw new InvalidEventException("Unpaired Unicode surrogate");
                        }
                        out.append(character).append(value.charAt(++index));
                    } else if (Character.isLowSurrogate(character)) {
                        throw new InvalidEventException("Unpaired Unicode surrogate");
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
    }
}
