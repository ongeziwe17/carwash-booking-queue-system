package com.carwash.booking.application;

import com.carwash.booking.domain.Booking;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.booking.infrastructure.InMemoryBookingRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookingCapacityQueryTest {

    private final InMemoryBookingRepository bookings = new InMemoryBookingRepository();
    private final BookingCapacityQuery query = new BookingCapacityQuery(bookings);

    @Test
    void countsOverlappingActiveBookings() {
        insert("one", LocalDateTime.of(2090, 1, 1, 10, 0), BookingStatus.CONFIRMED);
        insert("two", LocalDateTime.of(2090, 1, 1, 10, 15), BookingStatus.CREATED);

        assertEquals(2, query.maximumConcurrentActiveBookings("offering", 30));
    }

    @Test
    void treatsTouchingHalfOpenWindowsAsNonOverlapping() {
        insert("one", LocalDateTime.of(2090, 1, 1, 10, 0), BookingStatus.CONFIRMED);
        insert("two", LocalDateTime.of(2090, 1, 1, 10, 30), BookingStatus.CONFIRMED);

        assertEquals(1, query.maximumConcurrentActiveBookings("offering", 30));
    }

    @Test
    void ignoresReleasedAndUnscheduledBookings() {
        insert("cancelled", LocalDateTime.of(2090, 1, 1, 10, 0), BookingStatus.CANCELLED);
        insert("completed", LocalDateTime.of(2090, 1, 1, 10, 0), BookingStatus.COMPLETED);
        insert("unscheduled", null, BookingStatus.CONFIRMED);

        assertEquals(0, query.maximumConcurrentActiveBookings("offering", 30));
    }

    private void insert(String id, LocalDateTime startsAt, BookingStatus status) {
        Booking booking = new Booking();
        booking.setBookingId(id);
        booking.assignOperationalScope("branch", "offering");
        booking.setScheduledDateTime(startsAt);
        booking.setStatus(status);
        bookings.insert(booking);
    }
}
