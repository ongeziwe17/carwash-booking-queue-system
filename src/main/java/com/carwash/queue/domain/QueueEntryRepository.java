package com.carwash.queue.domain;

import com.carwash.shared.domain.Repository;

import com.carwash.queue.domain.QueueEntry;

import java.util.List;
import java.util.Optional;

public interface QueueEntryRepository extends Repository<QueueEntry, String> {
    List<QueueEntry> findAllOrdered();
    List<QueueEntry> findActiveOrdered();
    List<QueueEntry> findActiveOrderedByBranch(String branchId);
    Optional<QueueEntry> findNextWaiting();
    Optional<QueueEntry> findNextWaitingByBranch(String branchId);
    List<QueueEntry> findByBookingId(String bookingId);
    List<QueueEntry> findByServiceId(String serviceId);
    List<QueueEntry> findByBranchId(String branchId);

    boolean existsByBookingId(String bookingId);
    boolean existsActiveByBookingId(String bookingId);
    boolean existsByServiceId(String serviceId);
    boolean existsByServiceOfferingId(String serviceOfferingId);
}
