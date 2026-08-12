package com.carwash.marketplace.api.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WeeklyOperatingIntervalResponse(
        DayOfWeek dayOfWeek,
        LocalTime opensAt,
        LocalTime closesAt
) {
}
