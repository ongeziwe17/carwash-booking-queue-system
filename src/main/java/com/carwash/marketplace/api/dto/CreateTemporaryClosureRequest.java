package com.carwash.marketplace.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateTemporaryClosureRequest(
        @NotBlank @Size(max = 64) String closureId,
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt,
        @NotBlank @Size(max = 500) String reason
) {
    public CreateTemporaryClosureRequest {
        closureId = trim(closureId);
        reason = trim(reason);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
