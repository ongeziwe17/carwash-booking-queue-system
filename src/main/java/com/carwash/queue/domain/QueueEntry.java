package com.carwash.queue.domain;

import com.carwash.booking.domain.Booking;
import com.carwash.catalog.domain.Service;

import com.carwash.queue.domain.QueueStatus;
import lombok.AccessLevel;
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
    @Setter(AccessLevel.NONE)
    private String branchId;
    @Setter(AccessLevel.NONE)
    private String serviceOfferingId;
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
        if (booking != null && booking.getBranchId() != null && booking.getServiceOfferingId() != null) {
            assignOperationalScope(booking.getBranchId(), booking.getServiceOfferingId());
        }
    }

    public QueueEntry(String queueEntryId, Booking booking, Service service, int position) {
        this(queueEntryId, booking, service);
        this.queueStatus = QueueStatus.WAITING;
        this.joinedAt = LocalDateTime.now();
        updateQueueMetrics(position, 0);
    }

    public void assignOperationalScope(String branchId, String serviceOfferingId) {
        String normalizedBranchId = requireId(branchId, "Branch ID");
        String normalizedOfferingId = requireId(serviceOfferingId, "Service offering ID");
        if (this.branchId != null && !this.branchId.equals(normalizedBranchId)) {
            throw new IllegalStateException("Queue entry branch is immutable");
        }
        if (this.serviceOfferingId != null && !this.serviceOfferingId.equals(normalizedOfferingId)) {
            throw new IllegalStateException("Queue entry service offering is immutable");
        }
        this.branchId = normalizedBranchId;
        this.serviceOfferingId = normalizedOfferingId;
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

    private String requireId(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (normalized.length() > 64) {
            throw new IllegalArgumentException(field + " must not exceed 64 characters");
        }
        return normalized;
    }

}
