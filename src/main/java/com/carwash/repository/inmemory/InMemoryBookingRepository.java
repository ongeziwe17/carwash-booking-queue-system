package com.carwash.repository.inmemory;


import com.carwash.domain.Booking;
import com.carwash.repository.BookingRepository;

import java.time.LocalDateTime;
import java.util.List;

public class InMemoryBookingRepository extends InMemoryRepository<Booking, String> implements BookingRepository {
    @Override
    public List<Booking> findByUserId(String userId) {
        return storage.values().stream()
                .filter(booking -> booking.getUser() != null && booking.getUser().getUserId().equals(userId)).toList();
    }

    @Override
    public List<Booking> findByScheduledDateTime(LocalDateTime scheduledDateTime) {
        return storage.values().stream()
                .filter(booking -> booking.getScheduledDateTime() != null
                        && booking.getScheduledDateTime().equals(scheduledDateTime))
                .toList();
    }

    @Override
    protected String getId(Booking entity) {
        return entity.getBookingId();
    }
}
