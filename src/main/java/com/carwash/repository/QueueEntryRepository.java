package com.carwash.repository;

import com.carwash.domain.QueueEntry;

import java.util.List;

public interface QueueEntryRepository extends Repository<QueueEntry, String> {
    List<QueueEntry> findByServiceId(String serviceId);
}
