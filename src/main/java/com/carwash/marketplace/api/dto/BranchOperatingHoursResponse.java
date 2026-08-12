package com.carwash.marketplace.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BranchOperatingHoursResponse(
        String branchId,
        String timezone,
        List<WeeklyOperatingIntervalResponse> intervals,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public BranchOperatingHoursResponse {
        intervals = List.copyOf(intervals);
    }
}
