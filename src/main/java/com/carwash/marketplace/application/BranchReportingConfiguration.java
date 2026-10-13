package com.carwash.marketplace.application;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Current configuration only. No historical version ledger exists. */
public record BranchReportingConfiguration(List<WeeklyOperatingIntervalSnapshot> intervals,
                                            List<Closure> closures, LocalDateTime scheduleUpdatedAt) {
    public BranchReportingConfiguration { intervals = List.copyOf(intervals); closures = List.copyOf(closures); }
    public record Closure(Instant start, Instant end) { }
}
