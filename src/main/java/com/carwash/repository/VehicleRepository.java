package com.carwash.repository;

import com.carwash.domain.Vehicle;

import java.util.List;

public interface VehicleRepository extends Repository<Vehicle, String> {
    List<Vehicle> findByUserId(String userId);

    boolean existsByUserIdAndPlateNumberIgnoreCase(
            String userId,
            String plateNumber,
            String excludedVehicleId
    );
}
