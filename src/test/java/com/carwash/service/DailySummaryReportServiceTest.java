package com.carwash.service;

import com.carwash.api.dto.DailySummaryReportResponse;
import com.carwash.enums.BookingStatus;
import com.carwash.enums.QueueStatus;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailySummaryReportServiceTest extends ServiceTestSupport {

    @Test
    void dailySummaryReturnsZeroTotalsWhenNoDataExists() {
        DailySummaryReportResponse report = reportService.generateDailySummary(TestDates.futureDays(10).toLocalDate());
        assertEquals(0, report.totalBookings());
        assertEquals(0, report.totalQueueEntries());
        assertEquals(0, report.pendingWorkload());
    }

    @Test
    void dailySummaryCountsBookingsForSelectedDateOnly() {
        LocalDateTime reportDateTime = TestDates.futureDays(10);
        createSavedBooking(reportDateTime, BookingStatus.CREATED);
        createSavedBooking(reportDateTime.plusDays(1), BookingStatus.CREATED);
        DailySummaryReportResponse report = reportService.generateDailySummary(reportDateTime.toLocalDate());
        assertEquals(1, report.totalBookings());
    }

    @Test
    void dailySummaryCountsConfirmedBookings() {
        LocalDateTime reportDateTime = TestDates.futureDays(10);
        createSavedBooking(reportDateTime, BookingStatus.CONFIRMED);
        createSavedBooking(reportDateTime.plusMinutes(1), BookingStatus.CREATED);
        DailySummaryReportResponse report = reportService.generateDailySummary(reportDateTime.toLocalDate());
        assertEquals(2, report.totalBookings());
        assertEquals(1, report.confirmedBookings());
    }

    @Test
    void dailySummaryCountsCancelledBookings() {
        LocalDateTime reportDateTime = TestDates.futureDays(10);
        createSavedBooking(reportDateTime, BookingStatus.CANCELLED);
        createSavedBooking(reportDateTime.plusMinutes(1), BookingStatus.CONFIRMED);
        DailySummaryReportResponse report = reportService.generateDailySummary(reportDateTime.toLocalDate());
        assertEquals(1, report.cancelledBookings());
    }

    @Test
    void dailySummaryExcludesCancelledBookingsFromCompletedTotals() {
        LocalDateTime reportDateTime = TestDates.futureDays(10);
        createSavedBooking(reportDateTime, BookingStatus.CANCELLED);
        createSavedBooking(reportDateTime.plusMinutes(1), BookingStatus.COMPLETED);
        DailySummaryReportResponse report = reportService.generateDailySummary(reportDateTime.toLocalDate());
        assertEquals(1, report.completedBookings());
        assertEquals(1, report.cancelledBookings());
    }

    @Test
    void dailySummaryCountsQueueEntriesByStatus() {
        LocalDateTime reportDateTime = TestDates.futureDays(10);
        createSavedQueueEntry(reportDateTime, QueueStatus.WAITING);
        createSavedQueueEntry(reportDateTime.plusMinutes(1), QueueStatus.CALLED);
        createSavedQueueEntry(reportDateTime.plusMinutes(2), QueueStatus.IN_PROGRESS);
        createSavedQueueEntry(reportDateTime.plusMinutes(3), QueueStatus.COMPLETED);
        DailySummaryReportResponse report = reportService.generateDailySummary(reportDateTime.toLocalDate());
        assertEquals(4, report.totalQueueEntries());
        assertEquals(1, report.waitingQueueEntries());
        assertEquals(1, report.calledQueueEntries());
        assertEquals(1, report.inProgressQueueEntries());
        assertEquals(1, report.completedQueueEntries());
    }

    @Test
    void dailySummaryCalculatesPendingWorkloadCorrectly() {
        LocalDateTime reportDateTime = TestDates.futureDays(10);
        createSavedBooking(reportDateTime, BookingStatus.CREATED);
        createSavedBooking(reportDateTime.plusMinutes(1), BookingStatus.CONFIRMED);
        createSavedBooking(reportDateTime.plusMinutes(2), BookingStatus.IN_SERVICE);
        createSavedBooking(reportDateTime.plusMinutes(3), BookingStatus.CANCELLED);
        createSavedBooking(reportDateTime.plusMinutes(4), BookingStatus.COMPLETED);
        DailySummaryReportResponse report = reportService.generateDailySummary(reportDateTime.toLocalDate());
        assertEquals(3, report.pendingWorkload());
    }
}
