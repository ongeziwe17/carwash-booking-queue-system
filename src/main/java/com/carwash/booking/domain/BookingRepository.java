package com.carwash.booking.domain;

import com.carwash.shared.domain.Repository;

import com.carwash.booking.domain.Booking;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends Repository<Booking, String> {
    List<Booking> findByUserId(String userId);
    List<Booking> findByVehicleId(String vehicleId);
    List<Booking> findByServiceId(String serviceId);
    List<Booking> findByScheduledDateTime(LocalDateTime scheduledDateTime);

    boolean existsByUserId(String userId);
    boolean existsByVehicleId(String vehicleId);
    boolean existsByServiceId(String serviceId);
}
