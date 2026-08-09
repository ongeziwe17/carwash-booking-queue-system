package com.carwash.repository.inmemory;

import com.carwash.domain.QueueEntry;
import com.carwash.repository.QueueEntryRepository;

import java.util.List;

public class InMemoryQueueEntryRepository extends InMemoryRepository<QueueEntry, String>
        implements QueueEntryRepository {

    @Override
    public List<QueueEntry> findByBookingId(String bookingId) {
        return findMatching(queueEntry -> queueEntry.getBooking() != null
                && bookingId.equals(queueEntry.getBooking().getBookingId()));
    }

    @Override
    public List<QueueEntry> findByServiceId(String serviceId) {
        return findMatching(queueEntry -> queueEntry.getService() != null
                && serviceId.equals(queueEntry.getService().getServiceId()));
    }

    @Override
    public boolean existsByBookingId(String bookingId) {
        return anyMatch(queueEntry -> queueEntry.getBooking() != null
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
                && serviceId.equals(queueEntry.getService().getServiceId()));
    }

    @Override
    protected String getId(QueueEntry entity) {
        return entity.getQueueEntryId();
    }
}
