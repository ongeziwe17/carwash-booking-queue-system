package com.carwash.service;

import com.carwash.reporting.api.dto.DailySummaryReportResponse;
import com.carwash.booking.domain.Booking;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.queue.domain.QueueStatus;
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
        createSavedBooking(reportDateTime.plusMinutes(30), BookingStatus.CREATED);
        DailySummaryReportResponse report = reportService.generateDailySummary(reportDateTime.toLocalDate());
        assertEquals(2, report.totalBookings());
        assertEquals(1, report.confirmedBookings());
    }

    @Test
    void dailySummaryCountsCancelledBookings() {
        LocalDateTime reportDateTime = TestDates.futureDays(10);
        createSavedBooking(reportDateTime, BookingStatus.CANCELLED);
        createSavedBooking(reportDateTime.plusMinutes(30), BookingStatus.CONFIRMED);
        DailySummaryReportResponse report = reportService.generateDailySummary(reportDateTime.toLocalDate());
        assertEquals(1, report.cancelledBookings());
    }

    @Test
    void dailySummaryExcludesCancelledBookingsFromCompletedTotals() {
        LocalDateTime reportDateTime = TestDates.futureDays(10);
        createSavedBooking(reportDateTime, BookingStatus.CANCELLED);
        createSavedBooking(reportDateTime.plusMinutes(30), BookingStatus.COMPLETED);
        DailySummaryReportResponse report = reportService.generateDailySummary(reportDateTime.toLocalDate());
        assertEquals(1, report.completedBookings());
        assertEquals(1, report.cancelledBookings());
    }

    @Test
    void dailySummaryCountsQueueEntriesByStatus() {
        LocalDateTime reportDateTime = TestDates.futureDays(10);
        createSavedQueueEntry(reportDateTime, QueueStatus.WAITING);
        createSavedQueueEntry(reportDateTime.plusMinutes(30), QueueStatus.CALLED);
        createSavedQueueEntry(reportDateTime.plusMinutes(60), QueueStatus.IN_PROGRESS);
        createSavedQueueEntry(reportDateTime.plusMinutes(90), QueueStatus.COMPLETED);
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
        createSavedBooking(reportDateTime.plusMinutes(30), BookingStatus.CONFIRMED);
        createSavedBooking(reportDateTime.plusMinutes(60), BookingStatus.IN_SERVICE);
        createSavedBooking(reportDateTime.plusMinutes(90), BookingStatus.CANCELLED);
        createSavedBooking(reportDateTime.plusMinutes(120), BookingStatus.COMPLETED);
        DailySummaryReportResponse report = reportService.generateDailySummary(reportDateTime.toLocalDate());
        assertEquals(3, report.pendingWorkload());
    }

    @Test
    void dailySummaryReflectsSynchronizedOperationalLifecycle() {
        LocalDateTime scheduled = TestDates.futureDays(15);
        Booking booking = createConfirmedBooking(scheduled);
        QueueEntry queueEntry = queueRepository.findById(queueService.createQueueEntry(
                ids.queueEntry(), booking.getBookingId(), booking.getService().getServiceId())
                .getQueueEntryId()).orElseThrow();

        DailySummaryReportResponse before = reportService.generateDailySummary(scheduled.toLocalDate());
        assertEquals(1, before.confirmedBookings());
        assertEquals(1, before.waitingQueueEntries());
        assertEquals(1, before.pendingWorkload());

        queueService.callQueueEntry(queueEntry.getQueueEntryId());
        DailySummaryReportResponse called = reportService.generateDailySummary(scheduled.toLocalDate());
        assertEquals(1, called.confirmedBookings());
        assertEquals(1, called.calledQueueEntries());

        queueService.startService(queueEntry.getQueueEntryId());
        DailySummaryReportResponse during = reportService.generateDailySummary(scheduled.toLocalDate());
        assertEquals(BookingStatus.IN_SERVICE, booking.getStatus());
        assertEquals(1, during.inProgressQueueEntries());
        assertEquals(1, during.pendingWorkload());

        queueService.completeQueueEntry(queueEntry.getQueueEntryId());
        DailySummaryReportResponse completed = reportService.generateDailySummary(scheduled.toLocalDate());
        assertEquals(1, completed.completedBookings());
        assertEquals(1, completed.completedQueueEntries());
        assertEquals(0, completed.pendingWorkload());
    }
}
