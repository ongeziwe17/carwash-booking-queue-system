package com.carwash.repository.inmemory;

import com.carwash.domain.Booking;
import com.carwash.repository.BookingRepository;

import java.time.LocalDateTime;
import java.util.List;

public class InMemoryBookingRepository extends InMemoryRepository<Booking, String>
        implements BookingRepository {

    @Override
    public List<Booking> findByUserId(String userId) {
        return immutableSorted(storage.values().stream()
                .filter(booking -> booking.getUser() != null
                        && userId.equals(booking.getUser().getUserId()))
                .toList());
    }

    @Override
    public List<Booking> findByVehicleId(String vehicleId) {
        return immutableSorted(storage.values().stream()
                .filter(booking -> booking.getVehicle() != null
                        && vehicleId.equals(booking.getVehicle().getVehicleId()))
                .toList());
    }

    @Override
    public List<Booking> findByServiceId(String serviceId) {
        return immutableSorted(storage.values().stream()
                .filter(booking -> booking.getService() != null
                        && serviceId.equals(booking.getService().getServiceId()))
                .toList());
    }

    @Override
    public List<Booking> findByScheduledDateTime(LocalDateTime scheduledDateTime) {
        return immutableSorted(storage.values().stream()
                .filter(booking -> booking.getScheduledDateTime() != null
                        && booking.getScheduledDateTime().equals(scheduledDateTime))
                .toList());
    }

    @Override
    public boolean existsByUserId(String userId) {
        return storage.values().stream()
                .anyMatch(booking -> booking.getUser() != null
                        && userId.equals(booking.getUser().getUserId()));
    }

    @Override
    public boolean existsByVehicleId(String vehicleId) {
        return storage.values().stream()
                .anyMatch(booking -> booking.getVehicle() != null
                        && vehicleId.equals(booking.getVehicle().getVehicleId()));
    }

    @Override
    public boolean existsByServiceId(String serviceId) {
        return storage.values().stream()
                .anyMatch(booking -> booking.getService() != null
                        && serviceId.equals(booking.getService().getServiceId()));
    }

    @Override
    protected String getId(Booking entity) {
        return entity.getBookingId();
    }
}
