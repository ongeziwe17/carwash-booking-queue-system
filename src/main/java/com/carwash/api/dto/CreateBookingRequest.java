package com.carwash.api.dto;

import com.carwash.domain.Booking;
import com.carwash.domain.Service;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateBookingRequest(
        @NotBlank(message = "bookingId is required")  
        String bookingId,

        @NotBlank(message = "userId is required")
        String userId,

        @NotBlank(message = "vehicleId is required")
        String vehicleId,

        @NotBlank(message = "serviceId is required")
        String serviceId,

        @NotNull(message = "scheduledDateTime is required")
        @Future(message = "scheduledDateTime must be in the future")
        LocalDateTime scheduledDateTime,

        String specialRequest
) {

    public Booking toBooking() {
        User user = new User();
        user.setUserId(userId);

        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(vehicleId);

        Service service = new Service();
        service.setServiceId(serviceId);

        return new Booking(
                bookingId,
                user,
                vehicle,
                service,
                scheduledDateTime,
                specialRequest
        );
    }
}
