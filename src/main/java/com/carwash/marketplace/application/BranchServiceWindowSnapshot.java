package com.carwash.marketplace.application;

import java.time.Instant;
import java.time.ZonedDateTime;

/** Immutable Marketplace decision for one complete operational service window. */
public record BranchServiceWindowSnapshot(
        String branchId,
        Instant startsAt,
        Instant endsAt,
        String timezone,
        ZonedDateTime branchLocalStartsAt,
        ZonedDateTime branchLocalEndsAt,
        ZonedDateTime branchLocalOperatingWindowStartsAt,
        boolean effectiveActive,
        boolean withinWeeklyHours,
        boolean temporarilyClosed,
        boolean open,
        String applicableClosureId,
        String applicableClosureReason
) {
}
