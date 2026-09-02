package com.carwash.audit.application;

import com.carwash.audit.domain.AuditAction;
import com.carwash.audit.domain.AuditSource;

import java.util.Map;
import java.util.Objects;

public record AuditCommand(
        AuditAction successAction,
        AuditAction failureAction,
        AuditActor actor,
        String businessId,
        String targetType,
        String targetId,
        AuditSource source,
        Map<String, String> metadata
) {
    public AuditCommand {
        Objects.requireNonNull(successAction, "successAction is required");
        Objects.requireNonNull(failureAction, "failureAction is required");
        Objects.requireNonNull(actor, "actor is required");
        Objects.requireNonNull(source, "source is required");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AuditCommand action(
            AuditAction action, AuditActor actor, String targetType, String targetId, AuditSource source
    ) {
        return new AuditCommand(action, action, actor, actor.businessId(), targetType, targetId, source, Map.of());
    }

    public static AuditCommand actionForBusiness(
            AuditAction action, AuditActor actor, String businessId, String targetType, String targetId,
            AuditSource source
    ) {
        return new AuditCommand(action, action, actor, businessId, targetType, targetId, source, Map.of());
    }
}
