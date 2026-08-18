package com.carwash.booking.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Customer-safe, detached branch availability projection. */
public record BranchAvailabilitySnapshot(
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
        String serviceOfferingId,
        String serviceId,
        String serviceName,
        BigDecimal price,
        int estimatedDurationMin,
        OffsetDateTime availableStartAt,
        OffsetDateTime estimatedEndAt,
        int concurrentCapacity,
        int capacityRemaining,
        Integer queueWaitEstimateMin,
        BigDecimal distanceKm
) {
}
