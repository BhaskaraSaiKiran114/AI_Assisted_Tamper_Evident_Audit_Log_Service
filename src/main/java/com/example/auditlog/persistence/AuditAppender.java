package com.example.auditlog.persistence;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Map;
import java.util.UUID;

import com.example.auditlog.audit.AppendReceipt;
import com.example.auditlog.audit.CanonicalJson;
import com.example.auditlog.audit.EventInput;
import com.example.auditlog.audit.PayloadProtector.ProtectedPayload;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuditAppender {

    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();
    private final JdbcTemplate jdbc;
    private final CanonicalJson canonical;

    public AuditAppender(JdbcTemplate jdbc, CanonicalJson canonical) {
        this.jdbc = jdbc;
        this.canonical = canonical;
    }

    @Transactional(timeout = 15)
    public AppendReceipt append(UUID id, Instant ingested, EventInput input, ProtectedPayload protectedPayload) {
        Map<String, Object> head = jdbc.queryForMap(
                "SELECT last_sequence, last_hash FROM chain_head WHERE chain_id = 1 FOR UPDATE");
        long sequence = Math.addExact(((Number) head.get("last_sequence")).longValue(), 1L);
        String previous = (String) head.get("last_hash");
        Instant occurred = input.timestamp() == null ? ingested : input.timestamp();
        byte[] bytes = canonical.bytes(Map.ofEntries(
                Map.entry("schemaVersion", 1), Map.entry("eventId", id.toString()),
                Map.entry("sequence", sequence), Map.entry("eventType", input.eventType()),
                Map.entry("actorId", input.actorId()), Map.entry("resourceType", input.resourceType()),
                Map.entry("resourceId", input.resourceId()), Map.entry("timestamp", TIME.format(occurred)),
                Map.entry("ingestedAt", TIME.format(ingested)), Map.entry("payload", protectedPayload.payload()),
                Map.entry("previousHash", previous)));
        String hash = canonical.hash(bytes);
        jdbc.update("""
                INSERT INTO audit_event (event_id, event_sequence, event_type, actor_id, resource_type, resource_id,
                    occurred_at, ingested_at, schema_version, canonical_bytes, payload_json, previous_hash, record_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)
                """, id, sequence, input.eventType(), input.actorId(), input.resourceType(), input.resourceId(),
                OffsetDateTime.ofInstant(occurred, ZoneOffset.UTC), OffsetDateTime.ofInstant(ingested, ZoneOffset.UTC),
                bytes, new String(canonical.bytes(protectedPayload.payload()), StandardCharsets.UTF_8), previous, hash);
        protectedPayload.wrappedKeys().forEach((path, wrapped) -> jdbc.update(
                "INSERT INTO payload_key (event_id, field_path, key_version, wrapped_key) VALUES (?, ?, 1, ?)",
                id, path, wrapped));
        if (jdbc.update("UPDATE chain_head SET last_sequence = ?, last_hash = ? WHERE chain_id = 1", sequence, hash) != 1) {
            throw new IllegalStateException("Chain head unavailable");
        }
        return new AppendReceipt(id, sequence, occurred, ingested, previous, hash);
    }
}
