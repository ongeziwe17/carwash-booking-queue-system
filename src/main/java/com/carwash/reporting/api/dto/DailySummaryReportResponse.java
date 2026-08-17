package com.carwash.reporting.api.dto;

import java.time.LocalDate;

public record DailySummaryReportResponse (
    String scopeType,
    String scopeId,
    String timezone,
    LocalDate reportDate,
    long totalBookings,
    long confirmedBookings,
    long cancelledBookings,
    long completedBookings,
    long totalQueueEntries,
    long waitingQueueEntries,
    long calledQueueEntries,
    long inProgressQueueEntries,
    long completedQueueEntries,
    long pendingWorkload
){
}
