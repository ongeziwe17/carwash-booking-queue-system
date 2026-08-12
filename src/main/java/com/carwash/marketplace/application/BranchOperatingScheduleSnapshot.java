package com.carwash.marketplace.application;

import com.carwash.marketplace.domain.BranchOperatingSchedule;

import java.time.LocalDateTime;
import java.util.List;

public record BranchOperatingScheduleSnapshot(
        String branchId,
        String timezone,
        List<WeeklyOperatingIntervalSnapshot> intervals,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public BranchOperatingScheduleSnapshot {
        intervals = List.copyOf(intervals);
    }

    static BranchOperatingScheduleSnapshot from(BranchOperatingSchedule schedule, String timezone) {
        return new BranchOperatingScheduleSnapshot(
                schedule.getBranchId(),
                timezone,
                schedule.getIntervals().stream().map(WeeklyOperatingIntervalSnapshot::from).toList(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }

    static BranchOperatingScheduleSnapshot empty(String branchId, String timezone) {
        return new BranchOperatingScheduleSnapshot(branchId, timezone, List.of(), null, null);
    }
}
