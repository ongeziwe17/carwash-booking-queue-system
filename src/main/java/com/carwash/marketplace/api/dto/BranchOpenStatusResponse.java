package com.carwash.marketplace.api.dto;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

public record BranchOpenStatusResponse(
        String branchId,
        OffsetDateTime requestedAt,
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
