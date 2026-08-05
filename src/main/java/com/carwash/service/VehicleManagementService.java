package com.carwash.service;

import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.repository.UserRepository;
import com.carwash.repository.VehicleRepository;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.util.List;

public class VehicleManagementService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;


    public VehicleManagementService(VehicleRepository vehicleRepository, UserRepository userRepository) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
    }

    public Vehicle createVehicle(
            String userId,
            String vehicleId,
            String plateNumber,
            String vehicleType,
            String brand,
            String model,
            String color,
            String notes
    ) {
        return createVehicle(new Vehicle(vehicleId, plateNumber, vehicleType, brand, model, color, notes), userId);
    }

    public Vehicle createVehicle(Vehicle vehicle, String userId) {
        validateVehicle(vehicle);
        User owner = requireUser(userId);
        rejectDuplicatePlateForOwner(owner, vehicle.getPlateNumber());
        vehicle.setUserId(owner.getUserId());
        owner.getVehicles().add(vehicle);
        vehicleRepository.save(vehicle);
        userRepository.save(owner);
        return vehicle;
    }

    public Vehicle findById(String vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
    }

    public List<Vehicle> findAll() {
        return vehicleRepository.findAll();
    }

    public List<Vehicle> findByUserId(String userId) {
        requireUser(userId);
        return vehicleRepository.findByUserId(userId);
    }

    public Vehicle updateVehicle(
            String vehicleId,
            String plateNumber,
            String vehicleType,
            String brand,
            String model,
            String color,
            String notes
    ) {
        return updateVehicle(new Vehicle(vehicleId, plateNumber, vehicleType, brand, model, color, notes));
    }

    public Vehicle updateVehicle(Vehicle vehicle) {
        Vehicle existing = findById(vehicle.getVehicleId());
        validateVehicle(vehicle);
        existing.setPlateNumber(vehicle.getPlateNumber());
        existing.setVehicleType(vehicle.getVehicleType());
        existing.updateVehicleDetails(vehicle.getBrand(), vehicle.getModel(), vehicle.getColor(), vehicle.getNotes());
        vehicleRepository.save(existing);
        return existing;
    }

    public void deleteVehicle(String vehicleId) {
        findById(vehicleId);
        vehicleRepository.delete(vehicleId);
    }

    private void validateVehicle(Vehicle vehicle) {
        if (vehicle == null) throw new BusinessRuleViolationException("Vehicle is required");
        if (isBlank(vehicle.getPlateNumber())) throw new BusinessRuleViolationException("Plate number must not be blank");
        if (isBlank(vehicle.getVehicleType())) throw new BusinessRuleViolationException("Vehicle type must not be blank");
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private void rejectDuplicatePlateForOwner(User owner, String plateNumber) {
        boolean duplicate = owner.getVehicles().stream()
                .anyMatch(v -> v.getPlateNumber() != null && v.getPlateNumber().equalsIgnoreCase(plateNumber));
        if (duplicate) throw new BusinessRuleViolationException("Duplicate plate for owner");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
