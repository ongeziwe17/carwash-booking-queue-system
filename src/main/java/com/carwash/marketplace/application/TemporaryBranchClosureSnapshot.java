package com.carwash.marketplace.application;

import com.carwash.marketplace.domain.ClosureStatus;
import com.carwash.marketplace.domain.TemporaryBranchClosure;

import java.time.Instant;
import java.time.LocalDateTime;

public record TemporaryBranchClosureSnapshot(
        String closureId,
        String branchId,
        Instant startAt,
        Instant endAt,
        String reason,
        ClosureStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static TemporaryBranchClosureSnapshot from(TemporaryBranchClosure closure) {
        return new TemporaryBranchClosureSnapshot(
                closure.getClosureId(),
                closure.getBranchId(),
                closure.getStartAt(),
                closure.getEndAt(),
                closure.getReason(),
                closure.getStatus(),
                closure.getCreatedAt(),
                closure.getUpdatedAt()
        );
    }
}
