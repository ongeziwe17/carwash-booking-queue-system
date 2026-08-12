package com.carwash.queue.domain;

import com.carwash.shared.domain.Repository;

import com.carwash.queue.domain.QueueEntry;

import java.util.List;
import java.util.Optional;

public interface QueueEntryRepository extends Repository<QueueEntry, String> {
    List<QueueEntry> findAllOrdered();
    List<QueueEntry> findActiveOrdered();
    Optional<QueueEntry> findNextWaiting();
    List<QueueEntry> findByBookingId(String bookingId);
    List<QueueEntry> findByServiceId(String serviceId);

    boolean existsByBookingId(String bookingId);
    boolean existsActiveByBookingId(String bookingId);
    boolean existsByServiceId(String serviceId);
}
