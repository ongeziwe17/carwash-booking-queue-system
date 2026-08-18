package com.carwash.booking.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

/** Detached, immutable decision shared by booking commands and availability consumers. */
public record BranchAvailabilityDecisionSnapshot(
        String branchId,
        String serviceOfferingId,
        String serviceId,
        String serviceName,
        BigDecimal price,
        int estimatedDurationMin,
        String timezone,
        Instant startsAt,
        Instant endsAt,
        OffsetDateTime branchLocalStartsAt,
        OffsetDateTime branchLocalEndsAt,
        boolean effectiveEligible,
        boolean withinOperatingHours,
        boolean temporarilyClosed,
        int concurrentCapacity,
        int occupiedCapacity,
        int capacityRemaining,
        boolean available,
        BranchAvailabilityReason reason
) {
}
