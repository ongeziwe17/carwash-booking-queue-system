package com.carwash.reporting.api.dto;

import java.time.LocalDate;

public record DailySummaryReportResponse (
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
