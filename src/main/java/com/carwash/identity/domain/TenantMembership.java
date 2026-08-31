package com.carwash.identity.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/** The current operational business assignment for exactly one user. */
public record TenantMembership(String userId, String businessId, LocalDateTime assignedAt) {

    public TenantMembership {
        userId = requireId(userId, "User ID");
        businessId = requireId(businessId, "Business ID");
        Objects.requireNonNull(assignedAt, "Assignment time is required");
    }

    private static String requireId(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (normalized.length() > 64) {
            throw new IllegalArgumentException(field + " must not exceed 64 characters");
        }
        return normalized;
    }
}
