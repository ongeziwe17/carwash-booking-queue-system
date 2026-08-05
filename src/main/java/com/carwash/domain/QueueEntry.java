package com.carwash.domain;

import com.carwash.enums.QueueStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

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

    public QueueEntry(String queueEntryId, Booking booking, Service service, int position) {
        this.queueEntryId = queueEntryId;
        this.booking = booking;
        this.service = service;
        this.position = position;
        this.queueStatus = QueueStatus.WAITING;
        this.joinedAt = LocalDateTime.now();
        recalculateEstimatedWait();
    }

    public void joinQueue() {
        this.joinedAt = LocalDateTime.now();
        this.queueStatus = QueueStatus.WAITING;
        recalculateEstimatedWait();
    }

    public void updatePosition(int newPosition) {
        this.position = Math.max(newPosition, 1);
        recalculateEstimatedWait();
    }

    public boolean callNext() {
        if (queueStatus != QueueStatus.WAITING) return false;
        this.queueStatus = QueueStatus.CALLED;
        this.calledAt = LocalDateTime.now();
        return true;
    }

    public boolean startService() {
        if (queueStatus != QueueStatus.CALLED) return false;
        this.queueStatus = QueueStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
        return true;
    }

    public boolean complete() {
        if (queueStatus != QueueStatus.IN_PROGRESS) return false;
        this.queueStatus = QueueStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.estimatedWaitMin = 0;
        return true;
    }

    public void recalculateEstimatedWait() {
        int duration = service != null ? service.getEstimatedDurationMin() : 10;
        this.estimatedWaitMin = Math.max(position - 1, 0) * duration;
    }

}
