package com.carwash.testsupport;

import java.time.LocalDateTime;

public final class TestDates {

    private static final LocalDateTime FUTURE_BASE = LocalDateTime.of(2090, 1, 15, 9, 0);
    private static final LocalDateTime PAST = LocalDateTime.of(2000, 1, 15, 9, 0);

    private TestDates() {
    }

    public static LocalDateTime future() {
        return FUTURE_BASE;
    }

    public static LocalDateTime futureDays(int days) {
        return FUTURE_BASE.plusDays(days);
    }

    public static LocalDateTime past() {
        return PAST;
    }
}
