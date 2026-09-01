package com.carwash.vehicle.domain;

import com.carwash.shared.domain.Repository;

import com.carwash.vehicle.domain.Vehicle;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends Repository<Vehicle, String> {
    List<Vehicle> findByUserId(String userId);
    List<Vehicle> findByBusinessId(String businessId);
    Optional<Vehicle> findByIdAndBusinessId(String vehicleId, String businessId);
    Optional<Vehicle> findByIdAndUserId(String vehicleId, String userId);

    boolean updateForBusiness(Vehicle vehicle, String businessId);
    boolean updateForUser(Vehicle vehicle, String userId);
    boolean updateForAdministrator(Vehicle vehicle);
    boolean deleteForBusiness(String vehicleId, String businessId);
    boolean deleteForUser(String vehicleId, String userId);
    boolean deleteForAdministrator(String vehicleId);

    boolean existsByUserIdAndPlateNumberIgnoreCase(
            String userId,
            String plateNumber,
            String excludedVehicleId
    );
}
