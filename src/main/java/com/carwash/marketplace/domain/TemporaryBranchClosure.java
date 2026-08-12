package com.carwash.marketplace.domain;

import com.carwash.shared.exception.BusinessRuleViolationException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

public final class TemporaryBranchClosure {

    public static final int MAX_REASON_LENGTH = 500;

    private final String closureId;
    private final String branchId;
    private final Instant startAt;
    private final Instant endAt;
    private final String reason;
    private final ClosureStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public TemporaryBranchClosure(
            String closureId,
            String branchId,
            Instant startAt,
            Instant endAt,
            String reason,
            ClosureStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.closureId = requireText(closureId, "Closure ID", 64);
        this.branchId = requireText(branchId, "Branch ID", 64);
        this.startAt = Objects.requireNonNull(startAt, "Closure start is required");
        this.endAt = Objects.requireNonNull(endAt, "Closure end is required");
        if (!startAt.isBefore(endAt)) {
            throw new BusinessRuleViolationException("Closure start must be before closure end");
        }
        this.reason = requireText(reason, "Closure reason", MAX_REASON_LENGTH);
        this.status = Objects.requireNonNull(status, "Closure status is required");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Update time is required");
    }

    public String getClosureId() {
        return closureId;
    }

    public String getBranchId() {
        return branchId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public String getReason() {
        return reason;
    }

    public ClosureStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return status == ClosureStatus.ACTIVE;
    }

    public boolean covers(Instant requestedAt) {
        Objects.requireNonNull(requestedAt, "Requested instant is required");
        return isActive() && !requestedAt.isBefore(startAt) && requestedAt.isBefore(endAt);
    }

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return isActive() && startAt.isBefore(otherEnd) && otherStart.isBefore(endAt);
    }

    public TemporaryBranchClosure cancel(LocalDateTime changedAt) {
        return new TemporaryBranchClosure(
                closureId,
                branchId,
                startAt,
                endAt,
                reason,
                ClosureStatus.CANCELLED,
                createdAt,
                changedAt
        );
    }

    private static String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(field + " must not be blank");
        }
        if (value.length() > maximumLength) {
            throw new BusinessRuleViolationException(field + " must not exceed " + maximumLength + " characters");
        }
        return value;
    }
}
