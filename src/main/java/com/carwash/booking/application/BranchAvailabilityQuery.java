package com.carwash.booking.application;

import java.time.Instant;

/** Published point-in-time availability decision for future in-process consumers. */
public interface BranchAvailabilityQuery {

    BranchAvailabilityDecisionSnapshot evaluate(
            String branchId,
            String serviceOfferingId,
            Instant startsAt,
            String excludedBookingId
    );
}
