package com.carwash.queue.api.dto;
import com.carwash.queue.application.QueueEntrySnapshot;
import com.carwash.queue.domain.QueueStatus;
import java.time.LocalDateTime;
public record QueueListResponse(String queueEntryId, String bookingId, String userId, String branchId,
                                String serviceOfferingId, String serviceId, LocalDateTime scheduledDateTime,
                                int position, QueueStatus queueStatus, int estimatedWaitMin,
                                LocalDateTime joinedAt, LocalDateTime calledAt, LocalDateTime startedAt, LocalDateTime completedAt) {
    public static QueueListResponse from(QueueEntrySnapshot s) { return new QueueListResponse(s.queueEntryId(), s.bookingId(),
            s.userId(), s.branchId(), s.serviceOfferingId(), s.serviceId(), s.scheduledDateTime(), s.position(), s.status(),
            s.estimatedWaitMin(), s.joinedAt(), s.calledAt(), s.startedAt(), s.completedAt()); }
}
