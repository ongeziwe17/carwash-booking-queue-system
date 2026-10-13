package com.carwash.booking.application;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

/** Bounded, branch-scoped scheduled cohorts; callers authorize the canonical branch first. */
public interface BookingReportQuery {
    List<BookingReportBucket> scheduledCohorts(String branchId, LocalDate start, LocalDate endExclusive);

    /** Prospective scheduled occupancy clipped to the branch-local calendar day; current offering durations. */
    BigDecimal bookedMinutes(String branchId, LocalDate day, Map<String, Integer> durationMinutes);
}
