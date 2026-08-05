package com.carwash.repository.inmemory;

import com.carwash.domain.QueueEntry;
import com.carwash.repository.QueueEntryRepository;

import java.util.List;

public class InMemoryQueueEntryRepository extends InMemoryRepository<QueueEntry, String>
        implements QueueEntryRepository {

    @Override
    public List<QueueEntry> findByBookingId(String bookingId) {
        return immutableSorted(storage.values().stream()
                .filter(queueEntry -> queueEntry.getBooking() != null
                        && bookingId.equals(queueEntry.getBooking().getBookingId()))
                .toList());
    }

    @Override
    public List<QueueEntry> findByServiceId(String serviceId) {
        return immutableSorted(storage.values().stream()
                .filter(queueEntry -> queueEntry.getService() != null
                        && serviceId.equals(queueEntry.getService().getServiceId()))
                .toList());
    }

    @Override
    public boolean existsByBookingId(String bookingId) {
        return storage.values().stream()
                .anyMatch(queueEntry -> queueEntry.getBooking() != null
                        && bookingId.equals(queueEntry.getBooking().getBookingId()));
    }

    @Override
    public boolean existsByServiceId(String serviceId) {
        return storage.values().stream()
                .anyMatch(queueEntry -> queueEntry.getService() != null
                        && serviceId.equals(queueEntry.getService().getServiceId()));
    }

    @Override
    protected String getId(QueueEntry entity) {
        return entity.getQueueEntryId();
    }
}
