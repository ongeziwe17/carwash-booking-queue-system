package com.carwash.vehicle.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVehicleRequest(
        @NotBlank @Size(max = 64) String userId,
        @NotBlank @Size(max = 64) String vehicleId,
        @NotBlank @Size(max = 32) String plateNumber,
        @NotBlank @Size(max = 50) String vehicleType,
        @NotBlank @Size(max = 80) String brand,
        @NotBlank @Size(max = 80) String model,
        @Size(max = 40) String color,
        @Size(max = 500) String notes
) {
    public CreateVehicleRequest {
        userId = trim(userId);
        vehicleId = trim(vehicleId);
        plateNumber = trim(plateNumber);
        vehicleType = trim(vehicleType);
        brand = trim(brand);
        model = trim(model);
        color = trim(color);
        notes = trim(notes);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
