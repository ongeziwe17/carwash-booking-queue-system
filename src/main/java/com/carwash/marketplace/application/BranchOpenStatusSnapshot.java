package com.carwash.marketplace.application;

import java.time.Instant;
import java.time.ZonedDateTime;

public record BranchOpenStatusSnapshot(
        String branchId,
        Instant requestedAt,
        String timezone,
        ZonedDateTime branchLocalDateTime,
        boolean effectiveActive,
        boolean withinWeeklyHours,
        boolean temporarilyClosed,
        boolean open,
        String applicableClosureId,
        String applicableClosureReason
) {
}
