package com.carwash.reporting.application;

import com.carwash.reporting.api.dto.DailySummaryReportResponse;
import com.carwash.booking.domain.Booking;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.booking.application.BookingQuery;
import com.carwash.queue.application.QueueQuery;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class DailySummaryReportService {

    private final BookingQuery bookingQuery;
    private final QueueQuery queueQuery;
    private final InMemoryDataCoordinator coordinator;


    public DailySummaryReportService(BookingQuery bookingQuery,
                                     QueueQuery queueQuery,
                                     InMemoryDataCoordinator coordinator) {
        this.bookingQuery = Objects.requireNonNull(bookingQuery, "Booking query is required");
        this.queueQuery = Objects.requireNonNull(queueQuery, "Queue query is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
    }

    public DailySummaryReportResponse generateDailySummary(LocalDate reportDate) {
        if (reportDate == null) throw new IllegalArgumentException("Report date is required");
        return coordinator.read(() -> {
            List<Booking> bookingsForDate = bookingQuery.findAll().stream()
                    .filter(booking -> booking.getScheduledDateTime() != null)
                    .filter(booking -> booking.getScheduledDateTime().toLocalDate().equals(reportDate)).toList();
            List<QueueEntry> queueEntriesForDate = queueQuery.findAll().stream()
                    .filter(queueEntry -> queueEntry.getBooking() != null)
                    .filter(queueEntry -> queueEntry.getBooking().getScheduledDateTime() != null)
                    .filter(queueEntry -> queueEntry.getBooking().getScheduledDateTime().toLocalDate().equals(reportDate))
                    .toList();
            long confirmedBookings = countBookingsByStatus(bookingsForDate, BookingStatus.CONFIRMED);
            long cancelledBookings = countBookingsByStatus(bookingsForDate, BookingStatus.CANCELLED);
            long completedBookings = countBookingsByStatus(bookingsForDate, BookingStatus.COMPLETED);
            long pendingWorkload = bookingsForDate.stream()
                    .filter(booking -> booking.getStatus() != BookingStatus.CANCELLED)
                    .filter(booking -> booking.getStatus() != BookingStatus.COMPLETED).count();
            return new DailySummaryReportResponse(reportDate, bookingsForDate.size(), confirmedBookings,
                    cancelledBookings, completedBookings, queueEntriesForDate.size(),
                    countQueueEntriesByStatus(queueEntriesForDate, QueueStatus.WAITING),
                    countQueueEntriesByStatus(queueEntriesForDate, QueueStatus.CALLED),
                    countQueueEntriesByStatus(queueEntriesForDate, QueueStatus.IN_PROGRESS),
                    countQueueEntriesByStatus(queueEntriesForDate, QueueStatus.COMPLETED), pendingWorkload);
        });
    }

    private long countBookingsByStatus(List<Booking> bookings, BookingStatus status) {
        return bookings.stream().filter(booking -> booking.getStatus() == status).count();
    }

    private long countQueueEntriesByStatus(List<QueueEntry> queueEntries, QueueStatus status) {
        return queueEntries.stream().filter(queueEntry -> queueEntry.getQueueStatus() == status).count();
    }
}
