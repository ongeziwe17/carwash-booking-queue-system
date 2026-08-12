package com.carwash.vehicle.domain;

import com.carwash.shared.domain.Repository;

import com.carwash.vehicle.domain.Vehicle;

import java.util.List;

public interface VehicleRepository extends Repository<Vehicle, String> {
    List<Vehicle> findByUserId(String userId);

    boolean existsByUserIdAndPlateNumberIgnoreCase(
            String userId,
            String plateNumber,
            String excludedVehicleId
    );
}
