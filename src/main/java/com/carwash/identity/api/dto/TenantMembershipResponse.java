package com.carwash.identity.api.dto;

import java.time.LocalDateTime;

public record TenantMembershipResponse(String userId, String businessId, LocalDateTime assignedAt) {
}
