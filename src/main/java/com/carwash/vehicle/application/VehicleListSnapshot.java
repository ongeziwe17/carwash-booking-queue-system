package com.carwash.vehicle.application;

import com.carwash.vehicle.domain.Vehicle;

/** Detached scalar vehicle data captured while the authorized read snapshot is held. */
public record VehicleListSnapshot(String vehicleId, String userId, String plateNumber, String vehicleType,
                                  String brand, String model, String color, String notes) {
    public static VehicleListSnapshot from(Vehicle vehicle) {
        return new VehicleListSnapshot(vehicle.getVehicleId(), vehicle.getUserId(), vehicle.getPlateNumber(),
                vehicle.getVehicleType(), vehicle.getBrand(), vehicle.getModel(), vehicle.getColor(), vehicle.getNotes());
    }
}
