package com.carwash.service;

import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.repository.BookingRepository;
import com.carwash.repository.UserRepository;
import com.carwash.repository.VehicleRepository;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Objects;

public class VehicleManagementService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final InMemoryDataCoordinator coordinator;

    public VehicleManagementService(VehicleRepository vehicleRepository, UserRepository userRepository) {
        this(vehicleRepository, userRepository, null, new InMemoryDataCoordinator());
    }

    public VehicleManagementService(
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            InMemoryDataCoordinator coordinator
    ) {
        this.vehicleRepository = Objects.requireNonNull(vehicleRepository, "Vehicle repository is required");
        this.userRepository = Objects.requireNonNull(userRepository, "User repository is required");
        this.bookingRepository = bookingRepository;
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
    }

    public Vehicle createVehicle(String userId, String vehicleId, String plateNumber, String vehicleType,
                                 String brand, String model, String color, String notes) {
        return createVehicle(new Vehicle(vehicleId, plateNumber, vehicleType, brand, model, color, notes), userId);
    }

    public Vehicle createVehicle(Vehicle vehicle, String userId) {
        return coordinator.write(() -> {
            validateVehicle(vehicle);
            User owner = requireUser(userId);
            normalizeVehicle(vehicle);
            rejectDuplicatePlateForOwner(owner.getUserId(), vehicle.getPlateNumber(), null);
            vehicle.setUserId(owner.getUserId());
            if (!vehicleRepository.insert(vehicle)) {
                throw new BusinessRuleViolationException("Vehicle ID already exists");
            }
            owner.addVehicle(vehicle);
            if (!userRepository.update(owner)) {
                owner.removeVehicle(vehicle.getVehicleId());
                vehicleRepository.deleteById(vehicle.getVehicleId());
                throw new ResourceNotFoundException("User not found: " + userId);
            }
            return vehicle;
        });
    }

    public Vehicle findById(String vehicleId) {
        return coordinator.read(() -> requireVehicle(vehicleId));
    }

    public List<Vehicle> findAll() {
        return coordinator.read(vehicleRepository::findAll);
    }

    public List<Vehicle> findByUserId(String userId) {
        return coordinator.read(() -> {
            requireUser(userId);
            return vehicleRepository.findByUserId(userId);
        });
    }

    public Vehicle updateVehicle(String vehicleId, String plateNumber, String vehicleType,
                                 String brand, String model, String color, String notes) {
        return updateVehicle(new Vehicle(vehicleId, plateNumber, vehicleType, brand, model, color, notes));
    }

    public Vehicle updateVehicle(Vehicle vehicle) {
        return coordinator.write(() -> {
            if (vehicle == null) throw new BusinessRuleViolationException("Vehicle is required");
            Vehicle existing = requireVehicle(vehicle.getVehicleId());
            validateVehicle(vehicle);
            normalizeVehicle(vehicle);
            rejectDuplicatePlateForOwner(existing.getUserId(), vehicle.getPlateNumber(), existing.getVehicleId());
            existing.setPlateNumber(vehicle.getPlateNumber());
            existing.setVehicleType(vehicle.getVehicleType());
            existing.updateVehicleDetails(vehicle.getBrand(), vehicle.getModel(), vehicle.getColor(), vehicle.getNotes());
            if (!vehicleRepository.update(existing)) {
                throw new ResourceNotFoundException("Vehicle not found: " + existing.getVehicleId());
            }
            return existing;
        });
    }

    public void deleteVehicle(String vehicleId) {
        coordinator.write(() -> {
            Vehicle vehicle = requireVehicle(vehicleId);
            if (bookingRepository != null && bookingRepository.existsByVehicleId(vehicleId)) {
                throw new BusinessRuleViolationException("Vehicle cannot be deleted while bookings still reference it");
            }
            User owner = requireUser(vehicle.getUserId());
            if (!vehicleRepository.deleteById(vehicleId)) throw new ResourceNotFoundException("Vehicle not found: " + vehicleId);
            owner.removeVehicle(vehicleId);
            if (!userRepository.update(owner)) throw new ResourceNotFoundException("User not found: " + vehicle.getUserId());
        });
    }

    private void validateVehicle(Vehicle vehicle) {
        if (vehicle == null) throw new BusinessRuleViolationException("Vehicle is required");
        if (isBlank(vehicle.getVehicleId())) throw new BusinessRuleViolationException("Vehicle ID must not be blank");
        if (isBlank(vehicle.getPlateNumber())) throw new BusinessRuleViolationException("Plate number must not be blank");
        if (isBlank(vehicle.getVehicleType())) throw new BusinessRuleViolationException("Vehicle type must not be blank");
    }

    private void normalizeVehicle(Vehicle vehicle) {
        vehicle.setVehicleId(vehicle.getVehicleId().trim());
        vehicle.setPlateNumber(vehicle.getPlateNumber().trim());
        vehicle.setVehicleType(vehicle.getVehicleType().trim());
    }

    private Vehicle requireVehicle(String vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private void rejectDuplicatePlateForOwner(String ownerId, String plateNumber, String excludedVehicleId) {
        if (vehicleRepository.existsByUserIdAndPlateNumberIgnoreCase(ownerId, plateNumber, excludedVehicleId)) {
            throw new BusinessRuleViolationException("Duplicate plate for owner");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
