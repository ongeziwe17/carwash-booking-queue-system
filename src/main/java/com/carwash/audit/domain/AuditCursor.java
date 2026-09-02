package com.carwash.audit.domain;

import java.time.Instant;
import java.util.Objects;

public record AuditCursor(Instant occurredAt, String auditId) {
    public AuditCursor {
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (auditId == null || auditId.isBlank()) throw new IllegalArgumentException("auditId is required");
    }
}
