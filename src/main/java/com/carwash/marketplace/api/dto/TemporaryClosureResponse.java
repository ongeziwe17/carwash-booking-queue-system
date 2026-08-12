package com.carwash.marketplace.api.dto;

import com.carwash.marketplace.domain.ClosureStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record TemporaryClosureResponse(
        String closureId,
        String branchId,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String reason,
        ClosureStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
