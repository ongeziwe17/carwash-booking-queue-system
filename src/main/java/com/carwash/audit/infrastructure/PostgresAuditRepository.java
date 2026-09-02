package com.carwash.audit.infrastructure;

import com.carwash.audit.application.AuditMetadataPolicy;
import com.carwash.audit.domain.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Profile("postgres")
public final class PostgresAuditRepository implements AuditRepository {
    private static final TypeReference<Map<String, String>> METADATA_TYPE = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditMetadataPolicy metadataPolicy;

    public PostgresAuditRepository(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                   AuditMetadataPolicy metadataPolicy) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.metadataPolicy = metadataPolicy;
    }

    @Override
    public void append(AuditRecord record) {
        jdbc.update("""
                INSERT INTO audit_records(
                    audit_id, occurred_at, occurred_at_nano_remainder, actor_type, actor_user_id,
                    actor_role, business_id, action, target_type, target_id, outcome, reason_code,
                    correlation_id, metadata, event_source
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """, record.auditId(), Timestamp.from(databaseTime(record.occurredAt())), nanoRemainder(record.occurredAt()),
                record.actorType().name(), record.actorUserId(), record.actorRole(), record.businessId(),
                record.action().name(), record.targetType(), record.targetId(), record.outcome().name(),
                record.reasonCode(), record.correlationId(), writeMetadata(metadataPolicy.sanitize(record.metadata())),
                record.source().name());
    }

    @Override
    public List<AuditRecord> queryByBusinessId(String businessId, AuditQuery query) {
        if (businessId == null || businessId.isBlank()) throw new IllegalArgumentException("Business scope is required");
        return query(query, businessId);
    }

    @Override
    public List<AuditRecord> queryPlatform(AuditQuery query) { return query(query, null); }

    private List<AuditRecord> query(AuditQuery query, String businessId) {
        StringBuilder sql = new StringBuilder("""
                SELECT audit_id, occurred_at, occurred_at_nano_remainder, actor_type, actor_user_id,
                       actor_role, business_id, action, target_type, target_id, outcome, reason_code,
                       correlation_id, metadata::text, event_source
                FROM audit_records
                WHERE (occurred_at, occurred_at_nano_remainder) >= (?, ?)
                  AND (occurred_at, occurred_at_nano_remainder) <= (?, ?)
                """);
        ArrayList<Object> parameters = new ArrayList<>();
        parameters.add(Timestamp.from(databaseTime(query.from())));
        parameters.add(nanoRemainder(query.from()));
        parameters.add(Timestamp.from(databaseTime(query.to())));
        parameters.add(nanoRemainder(query.to()));
        if (businessId != null) add(sql, parameters, "business_id = ?", businessId);
        if (query.actorUserId() != null) add(sql, parameters, "actor_user_id = ?", query.actorUserId());
        if (query.action() != null) add(sql, parameters, "action = ?", query.action().name());
        if (query.outcome() != null) add(sql, parameters, "outcome = ?", query.outcome().name());
        if (query.targetType() != null) add(sql, parameters, "target_type = ?", query.targetType());
        if (query.targetId() != null) add(sql, parameters, "target_id = ?", query.targetId());
        if (query.cursor() != null) {
            sql.append(" AND (occurred_at, occurred_at_nano_remainder, audit_id) < (?, ?, ?)");
            parameters.add(Timestamp.from(databaseTime(query.cursor().occurredAt())));
            parameters.add(nanoRemainder(query.cursor().occurredAt()));
            parameters.add(query.cursor().auditId());
        }
        sql.append(" ORDER BY occurred_at DESC, occurred_at_nano_remainder DESC, audit_id DESC LIMIT ?");
        parameters.add(query.limit());
        return jdbc.query(sql.toString(), ROW_MAPPER, parameters.toArray());
    }

    private static void add(StringBuilder sql, List<Object> parameters, String predicate, Object value) {
        sql.append(" AND ").append(predicate);
        parameters.add(value);
    }

    private final RowMapper<AuditRecord> ROW_MAPPER = (result, row) -> new AuditRecord(
            result.getString("audit_id"),
            domainTime(result.getTimestamp("occurred_at").toInstant(), result.getShort("occurred_at_nano_remainder")),
            AuditActorType.valueOf(result.getString("actor_type")), result.getString("actor_user_id"),
            result.getString("actor_role"), result.getString("business_id"),
            AuditAction.valueOf(result.getString("action")), result.getString("target_type"),
            result.getString("target_id"), AuditOutcome.valueOf(result.getString("outcome")),
            result.getString("reason_code"), result.getString("correlation_id"),
            readMetadata(result.getString("metadata")), AuditSource.valueOf(result.getString("event_source")));

    private String writeMetadata(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception failure) {
            throw new IllegalStateException("Safe audit metadata could not be serialized", failure);
        }
    }

    private Map<String, String> readMetadata(String metadata) {
        try {
            return objectMapper.readValue(metadata, METADATA_TYPE);
        } catch (Exception failure) {
            throw new IllegalStateException("Stored audit metadata is invalid", failure);
        }
    }

    private static Instant databaseTime(Instant value) {
        return value.minusNanos(value.getNano() % 1_000L);
    }
    private static short nanoRemainder(Instant value) { return (short) (value.getNano() % 1_000); }
    private static Instant domainTime(Instant databaseValue, short remainder) {
        return databaseValue.plusNanos(remainder);
    }
}
