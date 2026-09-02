package com.carwash.audit.api;

import com.carwash.audit.domain.*;

import java.time.Instant;
import java.util.Map;

public record AuditRecordResponse(
        String auditId, Instant occurredAt, AuditActorType actorType, String actorUserId, String actorRole,
        String businessId, AuditAction action, String targetType, String targetId, AuditOutcome outcome,
        String reasonCode, String correlationId, Map<String, String> metadata, AuditSource source
) {
    static AuditRecordResponse from(AuditRecord record) {
        return new AuditRecordResponse(record.auditId(), record.occurredAt(), record.actorType(), record.actorUserId(),
                record.actorRole(), record.businessId(), record.action(), record.targetType(), record.targetId(),
                record.outcome(), record.reasonCode(), record.correlationId(), record.metadata(), record.source());
    }
}
