package com.carwash.repository.inmemory;

import com.carwash.domain.QueueEntry;
import com.carwash.repository.QueueEntryRepository;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;

import java.util.List;

public class InMemoryQueueEntryRepository extends InMemoryRepository<QueueEntry, String> implements QueueEntryRepository {
    @Override
    public List<QueueEntry> findByServiceId(String serviceId) {
        return storage.values().stream().filter(queueEntry -> queueEntry.getService() != null && queueEntry.getService().getServiceId().equals(serviceId)).toList();
    }

    @Override
    protected String getId(QueueEntry entity) {
        return entity.getQueueEntryId();
    }
}
