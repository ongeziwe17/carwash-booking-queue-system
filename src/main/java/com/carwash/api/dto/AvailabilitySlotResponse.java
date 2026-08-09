package com.carwash.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A currently bookable exact-start appointment slot.")
public record AvailabilitySlotResponse(
        LocalDateTime startDateTime,
        LocalDateTime estimatedEndDateTime,
        int capacityRemaining
) {
}
