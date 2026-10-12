package com.carwash.vehicle.api.dto;
import com.carwash.vehicle.domain.Vehicle;
public record VehicleListResponse(String vehicleId, String userId, String plateNumber, String vehicleType,
                                  String brand, String model, String color, String notes) {
    public static VehicleListResponse from(Vehicle v) { return new VehicleListResponse(v.getVehicleId(), v.getUserId(),
            v.getPlateNumber(), v.getVehicleType(), v.getBrand(), v.getModel(), v.getColor(), v.getNotes()); }
}
