package com.carwash.booking.application;

import com.carwash.booking.domain.Booking;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.catalog.application.ServiceOfferingCapacityQuery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Detached active-booking overlap query published by Booking for Catalogue. */
public final class BookingCapacityQuery implements ServiceOfferingCapacityQuery {

    private final BookingRepository bookingRepository;

    public BookingCapacityQuery(BookingRepository bookingRepository) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "Booking repository is required");
    }

    @Override
    public int maximumConcurrentActiveBookings(String offeringId, int estimatedDurationMin) {
        if (offeringId == null || offeringId.isBlank()) {
            throw new IllegalArgumentException("Offering ID is required");
        }
        if (estimatedDurationMin <= 0) {
            throw new IllegalArgumentException("Offering duration must be positive");
        }

        List<Boundary> boundaries = new ArrayList<>();
        bookingRepository.findByServiceOfferingId(offeringId).stream()
                .filter(this::occupiesCapacity)
                .map(Booking::getScheduledDateTime)
                .filter(Objects::nonNull)
                .forEach(start -> {
                    boundaries.add(new Boundary(start, 1));
                    boundaries.add(new Boundary(start.plusMinutes(estimatedDurationMin), -1));
                });
        boundaries.sort(Comparator.comparing(Boundary::at)
                .thenComparingInt(Boundary::delta));

        int active = 0;
        int maximum = 0;
        for (Boundary boundary : boundaries) {
            active += boundary.delta();
            maximum = Math.max(maximum, active);
        }
        return maximum;
    }

    private boolean occupiesCapacity(Booking booking) {
        return booking.getStatus() != BookingStatus.CANCELLED
                && booking.getStatus() != BookingStatus.COMPLETED;
    }

    private record Boundary(LocalDateTime at, int delta) {
    }
}
