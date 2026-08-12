package com.carwash.marketplace.application;

import java.util.List;

public record ReplaceOperatingScheduleCommand(
        List<WeeklyOperatingIntervalCommand> intervals
) {
    public ReplaceOperatingScheduleCommand {
        intervals = intervals == null ? null : List.copyOf(intervals);
    }
}
