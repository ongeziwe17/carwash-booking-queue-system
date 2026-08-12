package com.carwash.marketplace.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReplaceOperatingHoursRequest(
        @NotNull @Size(max = 100) List<@Valid WeeklyOperatingIntervalRequest> intervals
) {
    public ReplaceOperatingHoursRequest {
        intervals = intervals == null ? null : List.copyOf(intervals);
    }
}
