package com.carwash.shared.api.error;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A safe validation error for one request field or parameter.")
public record ApiFieldError(
        @Schema(example = "plateNumber") String field,
        @Schema(example = "must not be blank") String message
) {
}
