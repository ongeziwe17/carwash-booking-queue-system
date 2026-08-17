package com.carwash.queue.application;

import com.carwash.queue.domain.QueueStatus;

import java.time.LocalDateTime;

/** Immutable, detached queue projection published to other capabilities. */
public record QueueEntrySnapshot(
        String queueEntryId,
        String bookingId,
        String userId,
        String branchId,
        String serviceOfferingId,
        String serviceId,
        LocalDateTime scheduledDateTime,
        int position,
        QueueStatus status,
        int estimatedWaitMin,
        LocalDateTime joinedAt,
        LocalDateTime calledAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
