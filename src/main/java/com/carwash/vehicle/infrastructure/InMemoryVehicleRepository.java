package com.carwash.vehicle.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;

import com.carwash.vehicle.domain.Vehicle;
import com.carwash.vehicle.domain.VehicleRepository;
import com.carwash.booking.domain.BookingRepository;

import java.util.List;

public class InMemoryVehicleRepository extends InMemoryRepository<Vehicle, String>
        implements VehicleRepository {
    @Override public com.carwash.shared.application.PageResult<Vehicle> findPage(com.carwash.shared.application.ListQuery query) {
        var scope = query.scope();
        java.util.List<Vehicle> source = scope.businessId() == null ? findMatching(v -> true) : findByBusinessId(scope.businessId());
        return query.select(source, java.util.Map.ofEntries(java.util.Map.entry("id", (java.util.function.Function<Vehicle, Object>) v -> v.getVehicleId()),
                java.util.Map.entry("brand", (java.util.function.Function<Vehicle, Object>) v -> v.getBrand()),
                java.util.Map.entry("model", (java.util.function.Function<Vehicle, Object>) v -> v.getModel()),
                java.util.Map.entry("userId", (java.util.function.Function<Vehicle, Object>) v -> v.getUserId()),
                java.util.Map.entry("vehicleType", (java.util.function.Function<Vehicle, Object>) v -> v.getVehicleType())));
    }


    private final BookingRepository bookings;

    public InMemoryVehicleRepository() {
        this(null);
    }

    public InMemoryVehicleRepository(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Override
    public List<Vehicle> findByUserId(String userId) {
        return findMatching(vehicle -> vehicle.getUserId() != null && vehicle.getUserId().equals(userId));
    }

    @Override
    public List<Vehicle> findByBusinessId(String businessId) {
        if (bookings == null) return List.of();
        java.util.Set<String> ids = bookings.findByBusinessId(businessId).stream()
                .map(booking -> booking.getVehicle() == null ? null : booking.getVehicle().getVehicleId())
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return findMatching(vehicle -> ids.contains(vehicle.getVehicleId()));
    }

    @Override
    public java.util.Optional<Vehicle> findByIdAndBusinessId(String vehicleId, String businessId) {
        return findByBusinessId(businessId).stream()
                .filter(vehicle -> vehicleId.equals(vehicle.getVehicleId())).findFirst();
    }

    @Override
    public java.util.Optional<Vehicle> findByIdAndUserId(String vehicleId, String userId) {
        return findById(vehicleId).filter(vehicle -> userId.equals(vehicle.getUserId()));
    }

    @Override
    public boolean updateForBusiness(Vehicle vehicle, String businessId) {
        return updateMatching(vehicle.getVehicleId(), vehicle,
                current -> findByIdAndBusinessId(current.getVehicleId(), businessId).isPresent());
    }

    @Override
    public boolean updateForUser(Vehicle vehicle, String userId) {
        return updateMatching(vehicle.getVehicleId(), vehicle,
                current -> userId.equals(current.getUserId()));
    }

    @Override
    public boolean updateForAdministrator(Vehicle vehicle) {
        return updateMatching(vehicle.getVehicleId(), vehicle, current -> true);
    }

    @Override
    public boolean deleteForBusiness(String vehicleId, String businessId) {
        return deleteMatching(vehicleId,
                current -> findByIdAndBusinessId(current.getVehicleId(), businessId).isPresent());
    }

    @Override
    public boolean deleteForUser(String vehicleId, String userId) {
        return deleteMatching(vehicleId, current -> userId.equals(current.getUserId()));
    }

    @Override
    public boolean deleteForAdministrator(String vehicleId) {
        return deleteMatching(vehicleId, current -> true);
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
