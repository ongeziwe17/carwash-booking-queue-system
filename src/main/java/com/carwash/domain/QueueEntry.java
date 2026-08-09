package com.carwash.domain;

import com.carwash.enums.QueueStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.Duration;
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

    public void updatePosition(int newPosition, Duration defaultServiceDuration) {
        this.position = Math.max(newPosition, 1);
        recalculateEstimatedWait(defaultServiceDuration);
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

    public void recalculateEstimatedWait() {
        int duration = service != null ? service.getEstimatedDurationMin() : 0;
        this.estimatedWaitMin = Math.max(position - 1, 0) * duration;
    }

    public void recalculateEstimatedWait(Duration defaultServiceDuration) {
        Objects.requireNonNull(defaultServiceDuration, "Default service duration is required");
        int duration = service != null && service.getEstimatedDurationMin() > 0
                ? service.getEstimatedDurationMin()
                : durationInMinutes(defaultServiceDuration);
        this.estimatedWaitMin = Math.max(position - 1, 0) * duration;
    }

    private int durationInMinutes(Duration duration) {
        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        if (seconds % 60 != 0 || duration.getNano() != 0) minutes++;
        return Math.toIntExact(Math.max(minutes, 1));
    }

}
