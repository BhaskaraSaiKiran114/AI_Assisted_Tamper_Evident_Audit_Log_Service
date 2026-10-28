package com.example.auditlog.persistence;

import com.example.auditlog.security.ResourceScope;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.example.auditlog.audit.EventQuery;
import com.example.auditlog.audit.VerificationResult.ChainHead;
import com.example.auditlog.report.AccessReportQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditReadRepository {

    private final JdbcTemplate jdbc;

    public AuditReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ChainHead> head() {
        return jdbc.query("SELECT last_sequence, last_hash FROM chain_head WHERE chain_id = 1",
                (row, index) -> new ChainHead(row.getLong(1), row.getString(2))).stream().findFirst();
    }

    public Optional<StoredEvent> find(UUID id) {
        return jdbc.query("SELECT * FROM audit_event WHERE event_id = ?", this::map, id).stream().findFirst();
    }

    public Optional<String> hashAt(long sequence) {
        return jdbc.query("SELECT record_hash FROM audit_event WHERE event_sequence = ?",
                (row, index) -> row.getString(1), sequence).stream().findFirst();
    }

    public List<StoredEvent> page(EventQuery query, long after, long upper) {
        return page(query, after, upper, ResourceScope.internal());
    }

    public List<StoredEvent> page(EventQuery query, long after, long upper, ResourceScope scope) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_event WHERE event_sequence > ? AND event_sequence <= ?");
        List<Object> parameters = new ArrayList<>(List.of(after, upper));
        scope.constrain(sql, parameters);
        if (!query.includeArchived()) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM event_archive a WHERE a.event_id = audit_event.event_id)");
        }
        filter(sql, parameters, "actor_id", query.actorId());
        filter(sql, parameters, "resource_type", query.resourceType());
        filter(sql, parameters, "resource_id", query.resourceId());
        filter(sql, parameters, "event_type", query.eventType());
        if (query.from() != null) {
            sql.append(" AND occurred_at >= ?");
            parameters.add(OffsetDateTime.ofInstant(query.from(), ZoneOffset.UTC));
        }
        if (query.to() != null) {
            sql.append(" AND occurred_at < ?");
            parameters.add(OffsetDateTime.ofInstant(query.to(), ZoneOffset.UTC));
        }
        sql.append(" ORDER BY event_sequence ASC LIMIT ?");
        parameters.add(query.limit() + 1);
        return jdbc.query(sql.toString(), this::map, parameters.toArray());
    }

    public Map<String, String> wrappedKeys(UUID id) {
        return jdbc.query("SELECT field_path, wrapped_key FROM payload_key WHERE event_id = ? AND key_version = 1",
                rows -> {
                    Map<String, String> keys = new java.util.HashMap<>();
                    while (rows.next()) {
                        keys.put(rows.getString(1), rows.getString(2));
                    }
                    return keys;
                }, id);
    }

    public Stream<StoredEvent> streamAll() {
        return jdbc.queryForStream(connection -> {
            var statement = connection.prepareStatement("SELECT * FROM audit_event ORDER BY event_sequence ASC");
            statement.setFetchSize(100);
            return statement;
        }, this::map);
    }

    public Stream<StoredEvent> accessCandidates(AccessReportQuery query, long after, long upper, int scanLimit) {
        return accessCandidates(query, after, upper, scanLimit, ResourceScope.internal());
    }

    public Stream<StoredEvent> accessCandidates(AccessReportQuery query, long after, long upper, int scanLimit, ResourceScope scope) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_event WHERE event_sequence > ? AND event_sequence <= ? "
                + "AND resource_type = 'CLIENT_ACCOUNT' AND event_type IN ('READ', 'UPDATE', 'EXPORT') "
                + "AND occurred_at >= ? AND occurred_at < ?");
        List<Object> parameters = new ArrayList<>(List.of(after, upper, OffsetDateTime.ofInstant(query.from(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(query.to(), ZoneOffset.UTC)));
        scope.constrain(sql, parameters);
        filter(sql, parameters, "actor_id", query.actorId());
        filter(sql, parameters, "resource_id", query.accountId());
        filter(sql, parameters, "event_type", query.action());
        if (!query.includeArchived()) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM event_archive a WHERE a.event_id = audit_event.event_id)");
        }
        sql.append(" ORDER BY event_sequence ASC LIMIT ?");
        parameters.add(scanLimit + 1);
        return jdbc.queryForStream(connection -> {
            var statement = connection.prepareStatement(sql.toString());
            statement.setFetchSize(100);
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            return statement;
        }, this::map);
    }

    private void filter(StringBuilder sql, List<Object> parameters, String column, String value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = ?");
            parameters.add(value);
        }
    }

    private StoredEvent map(ResultSet row, int index) throws SQLException {
        return new StoredEvent(row.getObject("event_id", UUID.class), row.getLong("event_sequence"),
                row.getString("event_type"), row.getString("actor_id"), row.getString("resource_type"),
                row.getString("resource_id"), row.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                row.getObject("ingested_at", OffsetDateTime.class).toInstant(), row.getInt("schema_version"),
                row.getBytes("canonical_bytes"), row.getString("payload_json"), row.getString("previous_hash"),
                row.getString("record_hash"));
    }
}
