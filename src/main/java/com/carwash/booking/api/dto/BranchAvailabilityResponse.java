package com.carwash.booking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Bookable branch/offering availability at one explicitly requested instant.")
public record BranchAvailabilityResponse(
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
        @Schema(description = "Current branch queue estimate in minutes; null for a non-current branch-local date.")
        Integer queueWaitEstimateMin,
        @Schema(description = "Straight-line kilometres rounded to two decimals; null when no origin is supplied.")
        BigDecimal distanceKm
) {
}
