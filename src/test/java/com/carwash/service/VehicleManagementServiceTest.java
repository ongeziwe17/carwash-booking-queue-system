package com.carwash.service;

import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VehicleManagementServiceTest extends ServiceTestSupport {

    @Test
    void vehicleCreationSucceeds() {
        User user = registerUser();
        String vehicleId = ids.vehicle();
        Vehicle vehicle = new Vehicle(vehicleId, ids.plate(), "Sedan", "Toyota", "Corolla", "Blue", "");
        assertEquals(vehicleId, vehicleService.createVehicle(vehicle, user.getUserId()).getVehicleId());
    }

    @Test
    void vehicleLookupByUserReturnsSingleOwnedVehicle() {
        User user = registerUser();
        Vehicle created = createVehicle(user);
        List<Vehicle> vehicles = vehicleService.findByUserId(user.getUserId());
        assertEquals(1, vehicles.size());
        assertEquals(created.getVehicleId(), vehicles.getFirst().getVehicleId());
    }

    @Test
    void vehicleLookupByUserReturnsMultipleOwnedVehicles() {
        User user = registerUser();
        Vehicle first = createVehicle(user);
        Vehicle second = createVehicle(user);
        List<String> vehicleIds = vehicleService.findByUserId(user.getUserId()).stream()
                .map(Vehicle::getVehicleId).toList();
        assertEquals(2, vehicleIds.size());
        assertTrue(vehicleIds.contains(first.getVehicleId()));
        assertTrue(vehicleIds.contains(second.getVehicleId()));
    }

    @Test
    void vehicleLookupByUserExcludesOtherUsersVehicles() {
        User firstUser = registerUser();
        User secondUser = registerUser();
        Vehicle first = createVehicle(firstUser);
        createVehicle(secondUser);
        List<Vehicle> vehicles = vehicleService.findByUserId(firstUser.getUserId());
        assertEquals(1, vehicles.size());
        assertEquals(first.getVehicleId(), vehicles.getFirst().getVehicleId());
    }

    @Test
    void vehicleLookupByUserWithNoVehiclesReturnsEmptyList() {
        User user = registerUser();
        assertTrue(vehicleService.findByUserId(user.getUserId()).isEmpty());
    }

    @Test
    void vehicleCreationFailsWithBlankPlate() {
        User user = registerUser();
        Vehicle vehicle = new Vehicle(ids.vehicle(), " ", "Sedan", "Toyota", "Corolla", "Blue", "");
        assertThrows(BusinessRuleViolationException.class,
                () -> vehicleService.createVehicle(vehicle, user.getUserId()));
    }

    @Test
    void vehicleUpdateFailsWhenMissing() {
        Vehicle vehicle = new Vehicle(ids.vehicle(), ids.plate(), "Sedan", "Toyota", "Corolla", "Blue", "");
        assertThrows(ResourceNotFoundException.class, () -> vehicleService.updateVehicle(vehicle));
    }
}
