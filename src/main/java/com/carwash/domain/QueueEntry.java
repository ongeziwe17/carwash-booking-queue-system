package com.carwash.domain;

import com.carwash.enums.QueueStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Setter
@Getter
public class QueueEntry {
    private String queueEntryId;
    private Booking booking;
    private Service service;
    private int position;
    private QueueStatus queueStatus;
    private LocalDateTime joinedAt;
    private LocalDateTime calledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private int estimatedWaitMin;

    public QueueEntry() {
    }

    public QueueEntry(String queueEntryId, Booking booking, Service service) {
        this.queueEntryId = queueEntryId;
        this.booking = booking;
        this.service = service;
    }

    public QueueEntry(String queueEntryId, Booking booking, Service service, int position) {
        this(queueEntryId, booking, service);
        this.queueStatus = QueueStatus.WAITING;
        this.joinedAt = LocalDateTime.now();
        updateQueueMetrics(position, 0);
    }

    public boolean callNext() {
        return callNext(LocalDateTime.now());
    }

    public boolean callNext(LocalDateTime calledAt) {
        if (queueStatus != QueueStatus.WAITING) return false;
        this.queueStatus = QueueStatus.CALLED;
        this.calledAt = Objects.requireNonNull(calledAt, "Call time is required");
        return true;
    }

    public boolean startService() {
        return startService(LocalDateTime.now());
    }

    public boolean startService(LocalDateTime startedAt) {
        if (queueStatus != QueueStatus.CALLED) return false;
        this.queueStatus = QueueStatus.IN_PROGRESS;
        this.startedAt = Objects.requireNonNull(startedAt, "Start time is required");
        return true;
    }

    public boolean complete() {
        return complete(LocalDateTime.now());
    }

    public boolean complete(LocalDateTime completedAt) {
        if (queueStatus != QueueStatus.IN_PROGRESS) return false;
        this.queueStatus = QueueStatus.COMPLETED;
        this.completedAt = Objects.requireNonNull(completedAt, "Completion time is required");
        this.estimatedWaitMin = 0;
        return true;
    }

    public void updateQueueMetrics(int position, int estimatedWaitMin) {
        if (position <= 0) throw new IllegalArgumentException("Queue position must be positive");
        if (estimatedWaitMin < 0) throw new IllegalArgumentException("Estimated wait must not be negative");
        this.position = position;
        this.estimatedWaitMin = estimatedWaitMin;
    }

}
