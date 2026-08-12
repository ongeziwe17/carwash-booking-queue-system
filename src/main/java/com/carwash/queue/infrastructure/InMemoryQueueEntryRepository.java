package com.carwash.queue.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;

import com.carwash.queue.domain.QueueEntry;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.queue.domain.QueueEntryRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class InMemoryQueueEntryRepository extends InMemoryRepository<QueueEntry, String>
        implements QueueEntryRepository {

    private static final Comparator<QueueEntry> QUEUE_ORDER = Comparator
            .comparing((QueueEntry queueEntry) -> !isActive(queueEntry))
            .thenComparingInt(QueueEntry::getPosition)
            .thenComparing(QueueEntry::getJoinedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(QueueEntry::getQueueEntryId, Comparator.nullsLast(Comparator.naturalOrder()));

    @Override
    public List<QueueEntry> findAllOrdered() {
        return findMatching(queueEntry -> true).stream()
                .sorted(QUEUE_ORDER)
                .toList();
    }

    @Override
    public List<QueueEntry> findActiveOrdered() {
        return findMatching(InMemoryQueueEntryRepository::isActive).stream()
                .sorted(QUEUE_ORDER)
                .toList();
    }

    @Override
    public Optional<QueueEntry> findNextWaiting() {
        return findActiveOrdered().stream()
                .filter(queueEntry -> queueEntry.getQueueStatus() == QueueStatus.WAITING)
                .findFirst();
    }

    @Override
    public List<QueueEntry> findByBookingId(String bookingId) {
        return findMatching(queueEntry -> queueEntry.getBooking() != null
                && bookingId != null
                && bookingId.equals(queueEntry.getBooking().getBookingId()));
    }

    @Override
    public List<QueueEntry> findByServiceId(String serviceId) {
        return findMatching(queueEntry -> queueEntry.getService() != null
                && serviceId != null
                && serviceId.equals(queueEntry.getService().getServiceId())).stream()
                .sorted(QUEUE_ORDER)
                .toList();
    }

    @Override
    public boolean existsByBookingId(String bookingId) {
        return anyMatch(queueEntry -> queueEntry.getBooking() != null
                && bookingId != null
                && bookingId.equals(queueEntry.getBooking().getBookingId()));
    }

    @Override
    public boolean existsActiveByBookingId(String bookingId) {
        if (bookingId == null) return false;
        return anyMatch(queueEntry -> queueEntry.getBooking() != null
                && bookingId.equals(queueEntry.getBooking().getBookingId())
                && queueEntry.getQueueStatus() != null
                && queueEntry.getQueueStatus().isActive());
    }

    @Override
    public boolean existsByServiceId(String serviceId) {
        return anyMatch(queueEntry -> queueEntry.getService() != null
                && serviceId != null
                && serviceId.equals(queueEntry.getService().getServiceId()));
    }

    @Override
    protected String getId(QueueEntry entity) {
        return entity.getQueueEntryId();
    }

    private static boolean isActive(QueueEntry queueEntry) {
        return queueEntry.getQueueStatus() != null && queueEntry.getQueueStatus().isActive();
    }
}
