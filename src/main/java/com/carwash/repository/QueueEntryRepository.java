package com.carwash.repository;

import com.carwash.domain.QueueEntry;

import java.util.List;

public interface QueueEntryRepository extends Repository<QueueEntry, String> {
    List<QueueEntry> findAllOrdered();
    List<QueueEntry> findActiveOrdered();
    List<QueueEntry> findByBookingId(String bookingId);
    List<QueueEntry> findByServiceId(String serviceId);

    boolean existsByBookingId(String bookingId);
    boolean existsActiveByBookingId(String bookingId);
    boolean existsByServiceId(String serviceId);
}
