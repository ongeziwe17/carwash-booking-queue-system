package com.carwash.marketplace.api.dto;

import com.carwash.marketplace.domain.BranchStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BranchResponse(
        String branchId,
        String businessId,
        String branchName,
        String addressLine1,
        String addressLine2,
        String city,
        String province,
        String postalCode,
        String countryCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String timezone,
        BranchStatus status,
        boolean publicDiscoveryEnabled,
        boolean effectiveActive,
        boolean discoverable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
