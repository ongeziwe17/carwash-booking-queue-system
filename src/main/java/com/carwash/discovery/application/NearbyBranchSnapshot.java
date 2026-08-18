package com.carwash.discovery.application;

import java.math.BigDecimal;

/** Detached, customer-safe nearby branch projection. */
public record NearbyBranchSnapshot(
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
        BigDecimal distanceKm
) {
}
