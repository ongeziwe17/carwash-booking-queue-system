package com.carwash.vehicle.application;

import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.identity.domain.UserRepository;
import com.carwash.vehicle.domain.VehicleRepository;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.MutationLock;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.access.application.TenantAccessContext;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class VehicleManagementService implements VehicleQuery {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final DataTransactionOperations coordinator;
    private final MutationLock mutationLock;


    public VehicleManagementService(
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            DataTransactionOperations coordinator
    ) {
        this(vehicleRepository, userRepository, bookingRepository, coordinator, MutationLock.noOp());
    }

    public VehicleManagementService(
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            DataTransactionOperations coordinator,
            MutationLock mutationLock
    ) {
        this.vehicleRepository = Objects.requireNonNull(vehicleRepository, "Vehicle repository is required");
        this.userRepository = Objects.requireNonNull(userRepository, "User repository is required");
        this.bookingRepository = bookingRepository;
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.mutationLock = Objects.requireNonNull(mutationLock, "Mutation lock is required");
    }

    Vehicle createVehicle(String userId, String vehicleId, String plateNumber, String vehicleType,
                                 String brand, String model, String color, String notes) {
        return createVehicle(new Vehicle(vehicleId, plateNumber, vehicleType, brand, model, color, notes), userId);
    }

    public Vehicle createVehicle(
            TenantAccessContext access,
            String userId,
            String vehicleId,
            String plateNumber,
            String vehicleType,
            String brand,
            String model,
            String color,
            String notes
    ) {
        Vehicle vehicle = new Vehicle(vehicleId, plateNumber, vehicleType, brand, model, color, notes);
        return coordinator.write(() -> createVehicleInside(access, vehicle, userId));
    }

    Vehicle createVehicle(Vehicle vehicle, String userId) {
        return coordinator.write(() -> createVehicleInside(null, vehicle, userId));
    }

    public Vehicle findById(String vehicleId) {
        return coordinator.read(() -> requireVehicle(vehicleId));
    }

    public Vehicle findById(TenantAccessContext access, String vehicleId) {
        return coordinator.read(() -> requireAccessibleVehicle(access, vehicleId));
    }

    public List<Vehicle> findAll() {
        return coordinator.read(vehicleRepository::findAll);
    }

    public List<Vehicle> findAll(TenantAccessContext access) {
        return findAll(access, null);
    }

    public List<Vehicle> findAll(TenantAccessContext access, String administratorBusinessId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (access.isPlatformAdministrator()) {
            String businessId = normalizeBusinessId(administratorBusinessId);
            return coordinator.read(() -> vehicleRepository.findByBusinessId(businessId));
        }
        if (access.isOperational()) {
            if (administratorBusinessId != null) {
                throw new BusinessRuleViolationException("Tenant identity is derived from authentication");
            }
            return coordinator.read(() -> vehicleRepository.findByBusinessId(access.requireBusinessId()));
        }
        throw new AccessDeniedException("Operational vehicle access is required");
    }

    public List<Vehicle> findByUserId(String userId) {
        return coordinator.read(() -> {
            requireUser(userId);
            return vehicleRepository.findByUserId(userId);
        });
    }

    @Override
    public Optional<String> findOwnerId(String vehicleId) {
        return coordinator.read(() -> vehicleRepository.findById(vehicleId).map(Vehicle::getUserId));
    }

    @Override
    public boolean existsByUserId(String userId) {
        return coordinator.read(() -> !vehicleRepository.findByUserId(userId).isEmpty());
    }

    Vehicle updateVehicle(String vehicleId, String plateNumber, String vehicleType,
                                 String brand, String model, String color, String notes) {
        return updateVehicle(new Vehicle(vehicleId, plateNumber, vehicleType, brand, model, color, notes));
    }

    public Vehicle updateVehicle(
            TenantAccessContext access,
            String vehicleId,
            String plateNumber,
            String vehicleType,
            String brand,
            String model,
            String color,
            String notes
    ) {
        Vehicle update = new Vehicle(vehicleId, plateNumber, vehicleType, brand, model, color, notes);
        return coordinator.write(() -> updateVehicleInside(access, update));
    }

    Vehicle updateVehicle(Vehicle vehicle) {
        return coordinator.write(() -> updateVehicleInside(null, vehicle));
    }

    void deleteVehicle(String vehicleId) {
        coordinator.write(() -> deleteVehicleInside(null, vehicleId));
    }

    public void deleteVehicle(TenantAccessContext access, String vehicleId) {
        coordinator.write(() -> deleteVehicleInside(access, vehicleId));
    }

    private Vehicle createVehicleInside(TenantAccessContext access, Vehicle vehicle, String userId) {
        if (access != null) {
            if (access.isOperational()) {
                throw new AccessDeniedException("Operational users cannot create customer-owned vehicles");
            }
            access.requireSelf(userId);
        }
        validateVehicle(vehicle);
        String normalizedUserId = normalizeId(userId, "User ID");
        normalizeVehicle(vehicle);
        mutationLock.acquire(List.of(
                MutationLock.vehicle(vehicle.getVehicleId()), MutationLock.customer(normalizedUserId)));
        User owner = requireUser(normalizedUserId);
        rejectDuplicatePlateForOwner(owner.getUserId(), vehicle.getPlateNumber(), null);
        vehicle.setUserId(owner.getUserId());
        if (!vehicleRepository.insert(vehicle)) {
            throw new BusinessRuleViolationException("Vehicle ID already exists");
        }
        owner.addVehicle(vehicle);
        if (!userRepository.update(owner)) {
            ResourceNotFoundException failure = new ResourceNotFoundException("User not found");
            coordinator.compensate(failure, () -> {
                owner.removeVehicle(vehicle.getVehicleId());
                vehicleRepository.deleteById(vehicle.getVehicleId());
            });
            throw failure;
        }
        return vehicle;
    }

    private Vehicle updateVehicleInside(TenantAccessContext access, Vehicle requested) {
        if (requested == null) throw new BusinessRuleViolationException("Vehicle is required");
        String vehicleId = normalizeId(requested.getVehicleId(), "Vehicle ID");
        mutationLock.acquire(MutationLock.vehicle(vehicleId));
        Vehicle existing = requireVehicleForMutation(access, vehicleId);
        mutationLock.acquire(MutationLock.customer(existing.getUserId()));
        validateVehicle(requested);
        normalizeVehicle(requested);
        rejectDuplicatePlateForOwner(existing.getUserId(), requested.getPlateNumber(), existing.getVehicleId());
        Vehicle original = copy(existing);
        try {
            existing.setPlateNumber(requested.getPlateNumber());
            existing.setVehicleType(requested.getVehicleType());
            existing.updateVehicleDetails(
                    requested.getBrand(), requested.getModel(), requested.getColor(), requested.getNotes());
            if (!updateVehicleRecord(access, existing)) throw new ResourceNotFoundException("Vehicle not found");
            return existing;
        } catch (RuntimeException failure) {
            coordinator.compensate(failure, () -> restore(existing, original));
            throw failure;
        }
    }

    private void deleteVehicleInside(TenantAccessContext access, String vehicleId) {
        String normalizedId = normalizeId(vehicleId, "Vehicle ID");
        mutationLock.acquire(MutationLock.vehicle(normalizedId));
        Vehicle vehicle = requireVehicleForMutation(access, normalizedId);
        mutationLock.acquire(MutationLock.customer(vehicle.getUserId()));
        if (bookingRepository != null && bookingRepository.existsByVehicleId(normalizedId)) {
            throw new BusinessRuleViolationException("Vehicle cannot be deleted while bookings still reference it");
        }
        User owner = requireUser(vehicle.getUserId());
        boolean deleted = access == null || access.isPlatformAdministrator()
                ? vehicleRepository.deleteForAdministrator(normalizedId)
                : access.isOperational()
                ? vehicleRepository.deleteForBusiness(normalizedId, access.requireBusinessId())
                : vehicleRepository.deleteForUser(normalizedId, access.userId());
        if (!deleted) throw new ResourceNotFoundException("Vehicle not found");
        owner.removeVehicle(normalizedId);
        if (!userRepository.update(owner)) throw new ResourceNotFoundException("User not found");
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

    private Vehicle requireAccessibleVehicle(TenantAccessContext access, String vehicleId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        Optional<Vehicle> accessible = access.isPlatformAdministrator()
                ? vehicleRepository.findById(vehicleId)
                : access.isOperational()
                ? vehicleRepository.findByIdAndBusinessId(vehicleId, access.requireBusinessId())
                : vehicleRepository.findByIdAndUserId(vehicleId, access.userId());
        return accessible.orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
    }

    private Vehicle requireVehicleForMutation(TenantAccessContext access, String vehicleId) {
        Optional<Vehicle> accessible = access == null || access.isPlatformAdministrator()
                ? vehicleRepository.findById(vehicleId)
                : access.isOperational()
                ? vehicleRepository.findByIdAndBusinessId(vehicleId, access.requireBusinessId())
                : vehicleRepository.findByIdAndUserId(vehicleId, access.userId());
        return accessible.orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
    }

    private boolean updateVehicleRecord(TenantAccessContext access, Vehicle vehicle) {
        return access == null || access.isPlatformAdministrator()
                ? vehicleRepository.updateForAdministrator(vehicle)
                : access.isOperational()
                ? vehicleRepository.updateForBusiness(vehicle, access.requireBusinessId())
                : vehicleRepository.updateForUser(vehicle, access.userId());
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

    private String normalizeId(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessRuleViolationException(field + " is required");
        }
        if (normalized.length() > 64) {
            throw new BusinessRuleViolationException(field + " must not exceed 64 characters");
        }
        return normalized;
    }

    private Vehicle copy(Vehicle source) {
        Vehicle copy = new Vehicle(
                source.getVehicleId(), source.getPlateNumber(), source.getVehicleType(), source.getBrand(),
                source.getModel(), source.getColor(), source.getNotes());
        copy.setUserId(source.getUserId());
        return copy;
    }

    private void restore(Vehicle target, Vehicle source) {
        target.setPlateNumber(source.getPlateNumber());
        target.setVehicleType(source.getVehicleType());
        target.updateVehicleDetails(source.getBrand(), source.getModel(), source.getColor(), source.getNotes());
    }

    private String normalizeBusinessId(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessRuleViolationException(
                    "Business ID is required for platform administrator vehicle access");
        }
        if (normalized.length() > 64) {
            throw new BusinessRuleViolationException("Business ID must not exceed 64 characters");
        }
        return normalized;
    }
}
