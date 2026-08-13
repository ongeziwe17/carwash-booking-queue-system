package com.carwash.marketplace.domain;

import com.carwash.shared.exception.BusinessRuleViolationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BranchOperatingSchedule {

    public static final int MAX_INTERVALS = 100;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_DAY = 24L * 60L * 60L * NANOS_PER_SECOND;
    private static final long NANOS_PER_WEEK = 7L * NANOS_PER_DAY;

    private final String branchId;
    private final List<WeeklyOperatingInterval> intervals;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public BranchOperatingSchedule(
            String branchId,
            List<WeeklyOperatingInterval> intervals,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        if (branchId == null || branchId.isBlank()) {
            throw new BusinessRuleViolationException("Branch ID must not be blank");
        }
        this.branchId = branchId;
        this.intervals = validateAndCopy(intervals);
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Update time is required");
    }

    public String getBranchId() {
        return branchId;
    }

    public List<WeeklyOperatingInterval> getIntervals() {
        return intervals;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isOpenAt(LocalDateTime branchLocalDateTime) {
        Objects.requireNonNull(branchLocalDateTime, "Branch-local date and time is required");
        return intervals.stream().anyMatch(interval -> interval.contains(branchLocalDateTime));
    }

    public BranchOperatingSchedule replace(List<WeeklyOperatingInterval> replacement, LocalDateTime changedAt) {
        return new BranchOperatingSchedule(branchId, replacement, createdAt, changedAt);
    }

    private static List<WeeklyOperatingInterval> validateAndCopy(List<WeeklyOperatingInterval> intervals) {
        if (intervals == null) {
            throw new BusinessRuleViolationException("Operating intervals are required");
        }
        if (intervals.size() > MAX_INTERVALS) {
            throw new BusinessRuleViolationException(
                    "Operating schedule must not exceed " + MAX_INTERVALS + " intervals");
        }
        if (intervals.stream().anyMatch(Objects::isNull)) {
            throw new BusinessRuleViolationException("Operating intervals must not contain null values");
        }

        List<WeeklyOperatingInterval> ordered = intervals.stream()
                .sorted(WeeklyOperatingInterval.NATURAL_ORDER)
                .toList();
        Set<WeeklyOperatingInterval> unique = new HashSet<>();
        for (WeeklyOperatingInterval interval : ordered) {
            if (!unique.add(interval)) {
                throw new BusinessRuleViolationException("Duplicate weekly operating interval");
            }
        }

        List<WeeklyRange> ranges = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            WeeklyOperatingInterval interval = ordered.get(index);
            long start = startNano(interval);
            long end = endNano(interval, start);
            if (end <= NANOS_PER_WEEK) {
                ranges.add(new WeeklyRange(start, end, index));
            } else {
                ranges.add(new WeeklyRange(start, NANOS_PER_WEEK, index));
                ranges.add(new WeeklyRange(0, end - NANOS_PER_WEEK, index));
            }
        }

        for (int left = 0; left < ranges.size(); left++) {
            for (int right = left + 1; right < ranges.size(); right++) {
                WeeklyRange first = ranges.get(left);
                WeeklyRange second = ranges.get(right);
                if (first.intervalIndex() != second.intervalIndex() && first.overlaps(second)) {
                    throw new BusinessRuleViolationException("Weekly operating intervals must not overlap");
                }
            }
        }
        return List.copyOf(ordered);
    }

    private static long startNano(WeeklyOperatingInterval interval) {
        return (interval.dayOfWeek().getValue() - 1L) * NANOS_PER_DAY
                + interval.opensAt().toNanoOfDay();
    }

    private static long endNano(WeeklyOperatingInterval interval, long start) {
        long dayStart = start - interval.opensAt().toNanoOfDay();
        long end = dayStart + interval.closesAt().toNanoOfDay();
        if (interval.isOvernight()) {
            end += NANOS_PER_DAY;
        }
        return end;
    }

    private record WeeklyRange(long startInclusive, long endExclusive, int intervalIndex) {
        boolean overlaps(WeeklyRange other) {
            return startInclusive < other.endExclusive && other.startInclusive < endExclusive;
        }
    }
}
