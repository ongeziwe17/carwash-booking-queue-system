package com.carwash.audit.application;

import com.carwash.audit.domain.AuditAction;
import com.carwash.audit.domain.AuditOutcome;

import java.time.Instant;

public record AuditQueryRequest(
        String actorUserId, AuditAction action, AuditOutcome outcome, String targetType, String targetId,
        Instant from, Instant to, String cursor, Integer limit, AuditScope scope, String businessId
) { }
