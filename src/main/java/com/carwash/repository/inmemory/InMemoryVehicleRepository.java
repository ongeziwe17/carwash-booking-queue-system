package com.carwash.repository.inmemory;

import com.carwash.domain.Vehicle;
import com.carwash.repository.VehicleRepository;

import java.util.List;

public class InMemoryVehicleRepository extends InMemoryRepository<Vehicle, String> implements VehicleRepository {
    @Override
    public List<Vehicle> findByUserId(String userId) {
        return storage.values().stream().filter(vehicle -> vehicle.getUserId() != null && vehicle.getUserId().equals(userId)).toList();
    }

    @Override
    protected String getId(Vehicle entity) {
        return entity.getVehicleId();
    }
}
