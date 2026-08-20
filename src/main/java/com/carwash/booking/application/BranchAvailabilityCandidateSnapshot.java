package com.carwash.booking.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Customer-safe, detached inputs for point-in-time recommendation scoring. */
public record BranchAvailabilityCandidateSnapshot(
        String businessId,
        String businessName,
        String branchId,
        String branchName,
        String timezone,
        String serviceOfferingId,
        String serviceId,
        String serviceName,
        BigDecimal price,
        int serviceDurationMin,
        OffsetDateTime availableStartAt,
        OffsetDateTime estimatedEndAt,
        int concurrentCapacity,
        int capacityRemaining,
        Integer queueWaitEstimateMin,
        BigDecimal rawDistanceKm
) {
}
