package com.carwash.booking.application;

import java.util.List;

/**
 * Published Booking-owned query for detached, authoritatively eligible branch candidates.
 * Raw distance is intentionally retained for downstream ranking while public availability
 * responses continue to expose only rounded distance.
 */
public interface BranchAvailabilityCandidateQuery {

    List<BranchAvailabilityCandidateSnapshot> findEligibleCandidates(
            BranchAvailabilitySearchCriteria criteria
    );
}
