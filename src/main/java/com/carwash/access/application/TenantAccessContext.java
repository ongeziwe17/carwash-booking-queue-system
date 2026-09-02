package com.carwash.access.application;

import com.carwash.identity.domain.RoleName;
import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;

/** Canonical authenticated subject, role and tenant resolved from validated server state. */
public record TenantAccessContext(String userId, RoleName role, String businessId) {

    public TenantAccessContext {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Authenticated user ID is required");
        }
        Objects.requireNonNull(role, "Authenticated role is required");
        if (operational(role) && (businessId == null || businessId.isBlank())) {
            throw new IllegalArgumentException("Operational tenant membership is required");
        }
        if (!operational(role) && businessId != null) {
            throw new IllegalArgumentException("Non-operational identities cannot carry a tenant membership");
        }
    }

    public boolean isPlatformAdministrator() {
        return role == RoleName.PLATFORM_ADMIN;
    }

    public boolean isOperational() {
        return operational(role);
    }

    public boolean isCustomer() {
        return role == RoleName.CUSTOMER;
    }

    /** Canonical scalar snapshot for cross-capability security/audit contracts. */
    public String canonicalRoleName() {
        return role.name();
    }

    public String requireBusinessId() {
        if (!isOperational()) {
            throw new AccessDeniedException("Operational tenant access is required");
        }
        return businessId;
    }

    public void requirePlatformAdministrator() {
        if (!isPlatformAdministrator()) {
            throw new AccessDeniedException("Platform administrator access is required");
        }
    }

    public void requireSelf(String requestedUserId) {
        if (!isPlatformAdministrator() && !userId.equals(requestedUserId)) {
            throw new AccessDeniedException("Self-service access is required");
        }
    }

    private static boolean operational(RoleName role) {
        return role == RoleName.STAFF || role == RoleName.BUSINESS_OWNER;
    }
}
