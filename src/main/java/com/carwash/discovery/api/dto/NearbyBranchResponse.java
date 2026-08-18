package com.carwash.discovery.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Customer-safe nearby Marketplace branch with straight-line distance in kilometres.")
public record NearbyBranchResponse(
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
        @Schema(description = "Straight-line distance in kilometres, rounded to two decimal places using HALF_UP.",
                example = "12.35")
        BigDecimal distanceKm
) {
}
