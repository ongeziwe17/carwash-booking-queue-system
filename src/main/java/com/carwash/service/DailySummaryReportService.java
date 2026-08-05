package com.carwash.service;

import com.carwash.api.dto.DailySummaryReportResponse;
import com.carwash.domain.Booking;
import com.carwash.domain.QueueEntry;
import com.carwash.enums.BookingStatus;
import com.carwash.enums.QueueStatus;
import com.carwash.repository.BookingRepository;
import com.carwash.repository.QueueEntryRepository;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class DailySummaryReportService {

    private final BookingRepository bookingRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final InMemoryDataCoordinator coordinator;

    public DailySummaryReportService(BookingRepository bookingRepository,
                                     QueueEntryRepository queueEntryRepository) {
        this(bookingRepository, queueEntryRepository, new InMemoryDataCoordinator());
    }

    public DailySummaryReportService(BookingRepository bookingRepository,
                                     QueueEntryRepository queueEntryRepository,
                                     InMemoryDataCoordinator coordinator) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "Booking repository is required");
        this.queueEntryRepository = Objects.requireNonNull(queueEntryRepository, "Queue repository is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
    }

    public DailySummaryReportResponse generateDailySummary(LocalDate reportDate) {
        if (reportDate == null) throw new IllegalArgumentException("Report date is required");
        return coordinator.read(() -> {
            List<Booking> bookingsForDate = bookingRepository.findAll().stream()
                    .filter(booking -> booking.getScheduledDateTime() != null)
                    .filter(booking -> booking.getScheduledDateTime().toLocalDate().equals(reportDate)).toList();
            List<QueueEntry> queueEntriesForDate = queueEntryRepository.findAll().stream()
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
