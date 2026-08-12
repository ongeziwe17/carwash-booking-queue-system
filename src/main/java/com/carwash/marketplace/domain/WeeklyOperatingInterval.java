package com.carwash.marketplace.domain;

import com.carwash.shared.exception.BusinessRuleViolationException;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Objects;

public record WeeklyOperatingInterval(
        DayOfWeek dayOfWeek,
        LocalTime opensAt,
        LocalTime closesAt
) {

    public static final Comparator<WeeklyOperatingInterval> NATURAL_ORDER = Comparator
            .comparingInt((WeeklyOperatingInterval interval) -> interval.dayOfWeek().getValue())
            .thenComparing(WeeklyOperatingInterval::opensAt)
            .thenComparing(WeeklyOperatingInterval::closesAt);

    public WeeklyOperatingInterval {
        Objects.requireNonNull(dayOfWeek, "Operating day is required");
        Objects.requireNonNull(opensAt, "Opening time is required");
        Objects.requireNonNull(closesAt, "Closing time is required");
        if (opensAt.equals(closesAt)) {
            throw new BusinessRuleViolationException("Opening and closing times must be different");
        }
    }

    public boolean isOvernight() {
        return closesAt.isBefore(opensAt);
    }

    public boolean contains(LocalDateTime localDateTime) {
        Objects.requireNonNull(localDateTime, "Branch-local date and time is required");
        DayOfWeek requestedDay = localDateTime.getDayOfWeek();
        LocalTime requestedTime = localDateTime.toLocalTime();

        if (!isOvernight()) {
            return requestedDay == dayOfWeek
                    && !requestedTime.isBefore(opensAt)
                    && requestedTime.isBefore(closesAt);
        }

        if (requestedDay == dayOfWeek) {
            return !requestedTime.isBefore(opensAt);
        }
        return requestedDay == dayOfWeek.plus(1) && requestedTime.isBefore(closesAt);
    }
}
