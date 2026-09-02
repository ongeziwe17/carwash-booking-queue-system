package com.carwash.audit.domain;

import java.time.Instant;

public record AuditQuery(
        String actorUserId, AuditAction action, AuditOutcome outcome, String targetType, String targetId,
        Instant from, Instant to, AuditCursor cursor, int limit
) { }
