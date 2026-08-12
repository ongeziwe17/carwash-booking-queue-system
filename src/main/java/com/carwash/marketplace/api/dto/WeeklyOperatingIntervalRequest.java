package com.carwash.marketplace.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WeeklyOperatingIntervalRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime opensAt,
        @NotNull LocalTime closesAt
) {
}
