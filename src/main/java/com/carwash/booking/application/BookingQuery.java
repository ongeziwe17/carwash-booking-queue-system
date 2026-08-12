package com.carwash.booking.application;

import com.carwash.booking.domain.Booking;

import java.util.List;
import java.util.Optional;

/** Published booking read contract for reporting, authorization, and reference checks. */
public interface BookingQuery {

    List<Booking> findAll();

    Optional<String> findOwnerId(String bookingId);

    boolean existsByUserId(String userId);

    boolean existsByVehicleId(String vehicleId);

    boolean existsByServiceId(String serviceId);
}
