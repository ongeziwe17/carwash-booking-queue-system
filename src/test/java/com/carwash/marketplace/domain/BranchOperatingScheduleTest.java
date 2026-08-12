package com.carwash.marketplace.domain;

import com.carwash.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchOperatingScheduleTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2030, 1, 1, 8, 0);

    @Test
    void emptyScheduleIsClosed() {
        BranchOperatingSchedule schedule = schedule(List.of());

        assertFalse(schedule.isOpenAt(LocalDateTime.of(2030, 1, 7, 10, 0)));
    }

    @Test
    void sameDayIntervalUsesHalfOpenBoundaries() {
        BranchOperatingSchedule schedule = schedule(List.of(interval(DayOfWeek.MONDAY, "08:00", "17:00")));

        assertTrue(schedule.isOpenAt(LocalDateTime.of(2030, 1, 7, 8, 0)));
        assertTrue(schedule.isOpenAt(LocalDateTime.of(2030, 1, 7, 16, 59, 59)));
        assertFalse(schedule.isOpenAt(LocalDateTime.of(2030, 1, 7, 17, 0)));
        assertFalse(schedule.isOpenAt(LocalDateTime.of(2030, 1, 7, 7, 59, 59)));
    }

    @Test
    void multipleIntervalsPreserveMiddayGapAndAllowAdjacency() {
        BranchOperatingSchedule schedule = schedule(List.of(
                interval(DayOfWeek.MONDAY, "08:00", "12:00"),
                interval(DayOfWeek.MONDAY, "13:00", "17:00"),
                interval(DayOfWeek.TUESDAY, "08:00", "12:00"),
                interval(DayOfWeek.TUESDAY, "12:00", "17:00")
        ));

        assertTrue(schedule.isOpenAt(LocalDateTime.of(2030, 1, 7, 11, 59)));
        assertFalse(schedule.isOpenAt(LocalDateTime.of(2030, 1, 7, 12, 30)));
        assertTrue(schedule.isOpenAt(LocalDateTime.of(2030, 1, 8, 12, 0)));
    }

    @Test
    void equalTimesDuplicatesAndSameDayOverlapsAreRejected() {
        WeeklyOperatingInterval duplicate = interval(DayOfWeek.MONDAY, "08:00", "12:00");

        assertThrows(BusinessRuleViolationException.class,
                () -> interval(DayOfWeek.MONDAY, "08:00", "08:00"));
        assertThrows(BusinessRuleViolationException.class,
                () -> schedule(List.of(duplicate, duplicate)));
        assertThrows(BusinessRuleViolationException.class, () -> schedule(List.of(
                interval(DayOfWeek.MONDAY, "08:00", "12:00"),
                interval(DayOfWeek.MONDAY, "11:59", "17:00")
        )));
    }

    @Test
    void overnightIntervalCoversOpeningDayAndFollowingDayWithHalfOpenEnd() {
        BranchOperatingSchedule schedule = schedule(List.of(interval(DayOfWeek.FRIDAY, "20:00", "02:00")));

        assertTrue(schedule.isOpenAt(LocalDateTime.of(2030, 1, 11, 20, 0)));
        assertTrue(schedule.isOpenAt(LocalDateTime.of(2030, 1, 12, 1, 59, 59)));
        assertFalse(schedule.isOpenAt(LocalDateTime.of(2030, 1, 12, 2, 0)));
    }

    @Test
    void overnightOverlapOnFollowingDayIsRejectedButBoundaryAdjacencyIsAllowed() {
        assertThrows(BusinessRuleViolationException.class, () -> schedule(List.of(
                interval(DayOfWeek.FRIDAY, "20:00", "02:00"),
                interval(DayOfWeek.SATURDAY, "01:00", "04:00")
        )));

        schedule(List.of(
                interval(DayOfWeek.FRIDAY, "20:00", "02:00"),
                interval(DayOfWeek.SATURDAY, "02:00", "04:00")
        ));
    }

    @Test
    void sundayOvernightWrapsIntoMondayAndDetectsWeeklyBoundaryOverlap() {
        BranchOperatingSchedule schedule = schedule(List.of(
                interval(DayOfWeek.SUNDAY, "20:00", "02:00"),
                interval(DayOfWeek.MONDAY, "02:00", "06:00")
        ));
        assertTrue(schedule.isOpenAt(LocalDateTime.of(2030, 1, 6, 23, 0)));
        assertTrue(schedule.isOpenAt(LocalDateTime.of(2030, 1, 7, 1, 0)));

        assertThrows(BusinessRuleViolationException.class, () -> schedule(List.of(
                interval(DayOfWeek.SUNDAY, "20:00", "02:00"),
                interval(DayOfWeek.MONDAY, "01:00", "06:00")
        )));
    }

    @Test
    void intervalCollectionIsBoundedAndDetached() {
        List<WeeklyOperatingInterval> mutable = new java.util.ArrayList<>();
        mutable.add(interval(DayOfWeek.MONDAY, "08:00", "17:00"));
        BranchOperatingSchedule schedule = schedule(mutable);
        mutable.clear();

        assertTrue(schedule.isOpenAt(LocalDateTime.of(2030, 1, 7, 10, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> schedule.getIntervals().add(interval(DayOfWeek.TUESDAY, "08:00", "17:00")));

        List<WeeklyOperatingInterval> oversized = java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> interval(DayOfWeek.MONDAY,
                        LocalTime.of(0, 0).plusSeconds(index * 2L).toString(),
                        LocalTime.of(0, 0).plusSeconds(index * 2L + 1L).toString()))
                .toList();
        assertThrows(BusinessRuleViolationException.class, () -> schedule(oversized));
    }

    private BranchOperatingSchedule schedule(List<WeeklyOperatingInterval> intervals) {
        return new BranchOperatingSchedule("branch-001", intervals, CREATED_AT, CREATED_AT);
    }

    private WeeklyOperatingInterval interval(DayOfWeek day, String opensAt, String closesAt) {
        return new WeeklyOperatingInterval(day, LocalTime.parse(opensAt), LocalTime.parse(closesAt));
    }
}
