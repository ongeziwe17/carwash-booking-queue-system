package com.carwash.repository.inmemory;

import com.carwash.domain.Vehicle;
import com.carwash.repository.VehicleRepository;

import java.util.List;

public class InMemoryVehicleRepository extends InMemoryRepository<Vehicle, String>
        implements VehicleRepository {

    @Override
    public List<Vehicle> findByUserId(String userId) {
        return findMatching(vehicle -> vehicle.getUserId() != null && vehicle.getUserId().equals(userId));
    }

    @Override
    public boolean existsByUserIdAndPlateNumberIgnoreCase(
            String userId,
            String plateNumber,
            String excludedVehicleId
    ) {
        if (userId == null || plateNumber == null) {
            return false;
        }
        String normalizedPlate = plateNumber.trim();
        return anyMatch(vehicle -> vehicle.getUserId() != null
                && vehicle.getUserId().equals(userId)
                && vehicle.getPlateNumber() != null
                && vehicle.getPlateNumber().trim().equalsIgnoreCase(normalizedPlate)
                && (excludedVehicleId == null || !excludedVehicleId.equals(vehicle.getVehicleId())));
    }

    @Override
    protected String getId(Vehicle entity) {
        return entity.getVehicleId();
    }
}
