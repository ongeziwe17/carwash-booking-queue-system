package com.carwash.audit.application;

import com.carwash.audit.domain.AuditActorType;

public record AuditActor(AuditActorType type, String userId, String role, String businessId) {
    public static AuditActor user(String userId, String role, String businessId) {
        return new AuditActor(AuditActorType.USER, userId, role, businessId);
    }
    public static AuditActor anonymous() { return new AuditActor(AuditActorType.ANONYMOUS, null, null, null); }
    public static AuditActor system() { return new AuditActor(AuditActorType.SYSTEM, null, null, null); }
}
