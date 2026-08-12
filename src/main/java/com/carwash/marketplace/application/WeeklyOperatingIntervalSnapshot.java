package com.carwash.marketplace.application;

import com.carwash.marketplace.domain.WeeklyOperatingInterval;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WeeklyOperatingIntervalSnapshot(
        DayOfWeek dayOfWeek,
        LocalTime opensAt,
        LocalTime closesAt
) {
    static WeeklyOperatingIntervalSnapshot from(WeeklyOperatingInterval interval) {
        return new WeeklyOperatingIntervalSnapshot(
                interval.dayOfWeek(), interval.opensAt(), interval.closesAt());
    }
}
