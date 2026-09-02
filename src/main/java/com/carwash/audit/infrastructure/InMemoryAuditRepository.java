package com.carwash.audit.infrastructure;

import com.carwash.audit.application.AuditMetadataPolicy;
import com.carwash.audit.domain.*;
import com.carwash.shared.application.DataTransactionOperations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAuditRepository implements AuditRepository {
    private static final Comparator<AuditRecord> NEWEST_FIRST = Comparator
            .comparing(AuditRecord::occurredAt).thenComparing(AuditRecord::auditId).reversed();
    private final ConcurrentHashMap<String, AuditRecord> records = new ConcurrentHashMap<>();
    private final DataTransactionOperations transactions;
    private final AuditMetadataPolicy metadataPolicy;

    public InMemoryAuditRepository(DataTransactionOperations transactions, AuditMetadataPolicy metadataPolicy) {
        this.transactions = Objects.requireNonNull(transactions);
        this.metadataPolicy = Objects.requireNonNull(metadataPolicy);
    }

    @Override
    public void append(AuditRecord record) {
        Objects.requireNonNull(record, "Audit record is required");
        AuditRecord safeRecord = withMetadata(record, metadataPolicy.sanitize(record.metadata()));
        if (records.putIfAbsent(safeRecord.auditId(), safeRecord) != null) {
            throw new IllegalStateException("Audit identifier already exists");
        }
        transactions.onRollback(() -> records.remove(safeRecord.auditId(), safeRecord));
    }

    private static AuditRecord withMetadata(AuditRecord record, java.util.Map<String, String> metadata) {
        return new AuditRecord(record.auditId(), record.occurredAt(), record.actorType(), record.actorUserId(),
                record.actorRole(), record.businessId(), record.action(), record.targetType(), record.targetId(),
                record.outcome(), record.reasonCode(), record.correlationId(), metadata, record.source());
    }

    @Override
    public List<AuditRecord> queryByBusinessId(String businessId, AuditQuery query) {
        if (businessId == null || businessId.isBlank()) throw new IllegalArgumentException("Business scope is required");
        return select(query, businessId);
    }

    @Override
    public List<AuditRecord> queryPlatform(AuditQuery query) { return select(query, null); }

    private List<AuditRecord> select(AuditQuery query, String businessId) {
        Objects.requireNonNull(query, "Audit query is required");
        ArrayList<AuditRecord> selected = new ArrayList<>();
        records.values().stream()
                .filter(record -> businessId == null || businessId.equals(record.businessId()))
                .filter(record -> query.actorUserId() == null || query.actorUserId().equals(record.actorUserId()))
                .filter(record -> query.action() == null || query.action() == record.action())
                .filter(record -> query.outcome() == null || query.outcome() == record.outcome())
                .filter(record -> query.targetType() == null || query.targetType().equals(record.targetType()))
                .filter(record -> query.targetId() == null || query.targetId().equals(record.targetId()))
                .filter(record -> !record.occurredAt().isBefore(query.from()) && !record.occurredAt().isAfter(query.to()))
                .filter(record -> beforeCursor(record, query.cursor()))
                .sorted(NEWEST_FIRST)
                .limit(query.limit())
                .forEach(selected::add);
        return List.copyOf(selected);
    }

    private static boolean beforeCursor(AuditRecord record, AuditCursor cursor) {
        if (cursor == null) return true;
        int time = record.occurredAt().compareTo(cursor.occurredAt());
        return time < 0 || (time == 0 && record.auditId().compareTo(cursor.auditId()) < 0);
    }

    /** Test/bootstrap lifecycle hook; deliberately absent from the application repository port. */
    public void clearForTests() { records.clear(); }
}
