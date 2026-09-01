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
    List<QueueEntry> findByBusinessId(String businessId);
    List<QueueEntry> findByBranchIdAndBusinessId(String branchId, String businessId);
    Optional<QueueEntry> findByIdAndBusinessId(String queueEntryId, String businessId);
    Optional<QueueEntry> findByIdAndUserId(String queueEntryId, String userId);
    default Optional<String> findBookingIdById(String queueEntryId) {
        return findById(queueEntryId).map(QueueEntry::getBooking)
                .map(com.carwash.booking.domain.Booking::getBookingId);
    }
    Optional<QueueEntry> findNextWaitingByBranchIdAndBusinessId(String branchId, String businessId);

    boolean updateForBusiness(QueueEntry queueEntry, String businessId);
    boolean updateForUser(QueueEntry queueEntry, String userId);
    boolean updateForAdministrator(QueueEntry queueEntry);
    boolean deleteForBusiness(String queueEntryId, String businessId);
    boolean deleteForUser(String queueEntryId, String userId);
    boolean deleteForAdministrator(String queueEntryId);

    boolean existsByBookingId(String bookingId);
    boolean existsActiveByBookingId(String bookingId);
    boolean existsByServiceId(String serviceId);
    boolean existsByServiceOfferingId(String serviceOfferingId);
}
