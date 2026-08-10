package com.carwash.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Point-in-time service availability for the global single-location schedule.")
public record ServiceAvailabilityResponse(
        String serviceId,
        String serviceName,
        LocalDate date,
        int estimatedServiceDurationMin,
        int slotCapacity,
        List<AvailabilitySlotResponse> slots
) {
    public ServiceAvailabilityResponse {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }
}
