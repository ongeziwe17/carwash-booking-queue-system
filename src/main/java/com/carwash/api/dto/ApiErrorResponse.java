package com.carwash.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Stable, client-safe API error response.")
public record ApiErrorResponse(
        @Schema(example = "400") int status,
        @Schema(example = "VALIDATION_FAILED") String code,
        @Schema(example = "Request validation failed") String message,
        @Schema(example = "2026-08-05T08:30:00Z") Instant timestamp,
        @Schema(example = "/api/vehicles") String path,
        List<ApiFieldError> fieldErrors
) {
    public ApiErrorResponse {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
