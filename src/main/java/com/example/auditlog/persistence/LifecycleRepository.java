package com.example.auditlog.persistence;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LifecycleRepository {

    public record Action(UUID operationId, String type, String actor, String requestHash, UUID auditEventId,
            Instant completedAt, String detailJson) { }
    public record Marker(UUID eventId, String path, UUID operationId, Instant at) { }

    private final JdbcTemplate jdbc;
    private final boolean postgres;

    public LifecycleRepository(JdbcTemplate jdbc) throws SQLException {
        this.jdbc = jdbc;
        try (var connection = jdbc.getDataSource().getConnection()) {
            postgres = connection.getMetaData().getDatabaseProductName().equals("PostgreSQL");
        }
    }

    public void lockHead() {
        jdbc.queryForMap("SELECT last_sequence FROM chain_head WHERE chain_id = 1 FOR UPDATE");
    }

    public Optional<Action> action(UUID operationId) {
        return actions("operation_id", operationId).stream().findFirst();
    }

    public Optional<Action> actionForEvent(UUID eventId) {
        return actions("audit_event_id", eventId).stream().findFirst();
    }

    private List<Action> actions(String column, UUID id) {
        return jdbc.query("SELECT * FROM lifecycle_action WHERE " + column + " = ?", (row, index) ->
                new Action(row.getObject("operation_id", UUID.class), row.getString("action_type"), row.getString("actor_id"),
                        row.getString("request_hash"), row.getObject("audit_event_id", UUID.class),
                        row.getObject("completed_at", OffsetDateTime.class).toInstant(), row.getString("detail_json")), id);
    }

    public List<UUID> eligible(Instant cutoff, int limit) {
        return jdbc.query("""
                SELECT e.event_id FROM audit_event e WHERE e.ingested_at < ?
                AND SUBSTRING(e.event_type, 1, 15) <> 'AUDIT_INTERNAL_'
                AND NOT EXISTS (SELECT 1 FROM event_archive a WHERE a.event_id = e.event_id)
                ORDER BY e.event_sequence LIMIT ?
                """, (row, index) -> row.getObject(1, UUID.class), OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC), limit);
    }

    public void insertAction(Action action) {
        jdbc.update("""
                INSERT INTO lifecycle_action (operation_id, action_type, actor_id, request_hash, audit_event_id, completed_at, detail_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, action.operationId(), action.type(), action.actor(), action.requestHash(), action.auditEventId(),
                OffsetDateTime.ofInstant(action.completedAt(), ZoneOffset.UTC), action.detailJson());
    }

    public void archive(UUID eventId, UUID operationId, Instant at) {
        jdbc.update("INSERT INTO event_archive (event_id, operation_id, archived_at) VALUES (?, ?, ?)",
                eventId, operationId, OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
    }

    public void markRedacted(UUID eventId, String path, UUID operationId, Instant at) {
        jdbc.update("INSERT INTO payload_redaction (event_id, field_path, operation_id, redacted_at) VALUES (?, ?, ?, ?)",
                eventId, path, operationId, OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
    }

    public int eraseKey(UUID eventId, String path, UUID operationId) {
        if (postgres) {
            return jdbc.queryForObject("SELECT public.erase_audit_field_key(?, ?, ?)", Integer.class, eventId, path, operationId);
        }
        return jdbc.update("DELETE FROM payload_key WHERE event_id = ? AND field_path = ?", eventId, path);
    }

    public Optional<Marker> archiveMarker(UUID eventId) {
        return archives("event_id", eventId).stream().findFirst();
    }

    public List<Marker> archivesForOperation(UUID id) {
        return archives("operation_id", id);
    }

    private List<Marker> archives(String column, UUID id) {
        return jdbc.query("SELECT * FROM event_archive WHERE " + column + " = ?", (row, index) ->
                new Marker(row.getObject("event_id", UUID.class), null, row.getObject("operation_id", UUID.class),
                        row.getObject("archived_at", OffsetDateTime.class).toInstant()), id);
    }

    public List<Marker> redactionsForEvent(UUID id) {
        return redactions("event_id", id);
    }

    public List<Marker> redactionsForOperation(UUID id) {
        return redactions("operation_id", id);
    }

    private List<Marker> redactions(String column, UUID id) {
        return jdbc.query("SELECT * FROM payload_redaction WHERE " + column + " = ? ORDER BY field_path", (row, index) ->
                new Marker(row.getObject("event_id", UUID.class), row.getString("field_path"), row.getObject("operation_id", UUID.class),
                        row.getObject("redacted_at", OffsetDateTime.class).toInstant()), id);
    }
}
