package com.carwash.vehicle.application;

import java.util.Optional;

/** Published vehicle read contract for ownership and reference checks. */
public interface VehicleQuery {

    Optional<String> findOwnerId(String vehicleId);

    boolean existsByUserId(String userId);
}
