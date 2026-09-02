package com.carwash.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Immutable historical security/operations snapshot. It deliberately has no operational foreign keys. */
public record AuditRecord(
        String auditId,
        Instant occurredAt,
        AuditActorType actorType,
        String actorUserId,
        String actorRole,
        String businessId,
        AuditAction action,
        String targetType,
        String targetId,
        AuditOutcome outcome,
        String reasonCode,
        String correlationId,
        Map<String, String> metadata,
        AuditSource source
) {
    public AuditRecord {
        auditId = required(auditId, "auditId", 64);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        actorType = Objects.requireNonNull(actorType, "actorType is required");
        actorUserId = optional(actorUserId, "actorUserId", 64);
        actorRole = optional(actorRole, "actorRole", 32);
        businessId = optional(businessId, "businessId", 64);
        action = Objects.requireNonNull(action, "action is required");
        targetType = required(targetType, "targetType", 64);
        targetId = optional(targetId, "targetId", 64);
        outcome = Objects.requireNonNull(outcome, "outcome is required");
        reasonCode = optional(reasonCode, "reasonCode", 64);
        correlationId = required(correlationId, "correlationId", 128);
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(metadata, "metadata is required")));
        source = Objects.requireNonNull(source, "source is required");
        if (actorType == AuditActorType.USER && (actorUserId == null || actorRole == null)) {
            throw new IllegalArgumentException("USER actors require canonical user identity and role");
        }
        if (actorType != AuditActorType.USER && (actorUserId != null || actorRole != null)) {
            throw new IllegalArgumentException("Only USER actors can contain user identity");
        }
        if ((outcome == AuditOutcome.SUCCESS) != (reasonCode == null)) {
            throw new IllegalArgumentException("Only denied or failed audit records require a reason code");
        }
    }

    private static String required(String value, String name, int max) {
        String normalized = optional(value, name, max);
        if (normalized == null) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }

    private static String optional(String value, String name, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(name + " must contain 1 to " + max + " characters");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " contains control characters");
        }
        return normalized;
    }
}
