package com.carwash.queue.application;

import com.carwash.queue.domain.QueueEntry;

import java.util.List;
import java.util.Optional;

/** Published queue read contract for reporting, authorization, and reference checks. */
public interface QueueQuery {

    List<QueueEntry> findAll();

    Optional<String> findOwnerId(String queueEntryId);

    boolean existsByServiceId(String serviceId);
}
