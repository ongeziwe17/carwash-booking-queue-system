package com.carwash.queue.application;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Bounded event aggregates; sourceZone is the application clock zone of stored local timestamps. */
public interface QueueReportQuery {
    List<QueueReportBucket> events(String branchId, LocalDate start, LocalDate endExclusive,
                                   ZoneId branchZone, ZoneId sourceZone);
}
