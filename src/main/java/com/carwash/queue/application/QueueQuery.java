package com.carwash.queue.application;

import java.util.List;
import java.util.Optional;

/** Published queue read contract for reporting, authorization, and reference checks. */
public interface QueueQuery {

    List<QueueEntrySnapshot> findQueueEntrySnapshots();

    List<QueueEntrySnapshot> findQueueEntrySnapshotsByBranch(String branchId);

    Optional<String> findOwnerId(String queueEntryId);

    boolean existsByServiceId(String serviceId);
}
