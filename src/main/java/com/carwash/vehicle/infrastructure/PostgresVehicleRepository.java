package com.carwash.vehicle.infrastructure;

import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.infrastructure.PersistenceSupport;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.vehicle.domain.VehicleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("postgres")
@Transactional
public class PostgresVehicleRepository implements VehicleRepository {
    private final VehicleSpringDataRepository repository;

    public PostgresVehicleRepository(VehicleSpringDataRepository repository) { this.repository = repository; }

    @Override public List<Vehicle> findByUserId(String userId) {
        return repository.findByUserIdOrderByIdAsc(userId).stream().map(this::domain).toList();
    }
    @Override public boolean existsByUserIdAndPlateNumberIgnoreCase(String userId, String plate, String excludedId) {
        return userId != null && plate != null && repository.duplicatePlate(userId, plate, excludedId);
    }
    @Override public boolean insert(Vehicle vehicle) {
        if (repository.existsById(vehicle.getVehicleId())) return false;
        try { repository.saveAndFlush(entity(vehicle)); return true; }
        catch (DataIntegrityViolationException failure) {
            if (PersistenceSupport.constraint(failure, "uq_vehicle_owner_plate_ci")) {
                throw new BusinessRuleViolationException("Duplicate plate for owner");
            }
            throw new BusinessRuleViolationException("Vehicle conflicts with existing data");
        }
    }
    @Override public boolean update(Vehicle vehicle) {
        Optional<VehicleJpaEntity> found = repository.findById(vehicle.getVehicleId());
        if (found.isEmpty()) return false;
        apply(vehicle, found.get());
        try { repository.saveAndFlush(found.get()); return true; }
        catch (DataIntegrityViolationException failure) {
            if (PersistenceSupport.constraint(failure, "uq_vehicle_owner_plate_ci")) {
                throw new BusinessRuleViolationException("Duplicate plate for owner");
            }
            throw failure;
        }
    }
    @Override public Optional<Vehicle> findById(String id) { return repository.findById(id).map(this::domain); }
    @Override public List<Vehicle> findAll() { return repository.findAllByOrderByIdAsc().stream().map(this::domain).toList(); }
    @Override public boolean deleteById(String id) {
        Optional<VehicleJpaEntity> found = repository.findById(id); if (found.isEmpty()) return false;
        repository.delete(found.get()); repository.flush(); return true;
    }
    @Override public boolean existsById(String id) { return repository.existsById(id); }

    private Vehicle domain(VehicleJpaEntity entity) {
        Vehicle value = new Vehicle(entity.id, entity.plateNumber, entity.vehicleType, entity.brand,
                entity.model, entity.color, entity.notes); value.setUserId(entity.userId); return value;
    }
    private static VehicleJpaEntity entity(Vehicle value) { VehicleJpaEntity entity = new VehicleJpaEntity(); apply(value, entity); return entity; }
    private static void apply(Vehicle value, VehicleJpaEntity entity) {
        entity.id = value.getVehicleId(); entity.userId = value.getUserId(); entity.plateNumber = value.getPlateNumber();
        entity.vehicleType = value.getVehicleType(); entity.brand = value.getBrand(); entity.model = value.getModel();
        entity.color = value.getColor(); entity.notes = value.getNotes();
    }
}
