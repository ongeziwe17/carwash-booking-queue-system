package com.carwash.marketplace.application;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WeeklyOperatingIntervalCommand(
        DayOfWeek dayOfWeek,
        LocalTime opensAt,
        LocalTime closesAt
) {
}
