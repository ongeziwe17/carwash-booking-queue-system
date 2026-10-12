package com.carwash.vehicle.api.dto;
import com.carwash.vehicle.application.VehicleListSnapshot;
public record VehicleListResponse(String vehicleId, String userId, String plateNumber, String vehicleType,
                                  String brand, String model, String color, String notes) {
    public static VehicleListResponse from(VehicleListSnapshot v) { return new VehicleListResponse(v.vehicleId(), v.userId(),
            v.plateNumber(), v.vehicleType(), v.brand(), v.model(), v.color(), v.notes()); }
}
