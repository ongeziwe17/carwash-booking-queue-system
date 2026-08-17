package com.carwash.booking.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;

import com.carwash.booking.domain.Booking;
import com.carwash.booking.domain.BookingRepository;

import java.time.LocalDateTime;
import java.util.List;

public class InMemoryBookingRepository extends InMemoryRepository<Booking, String>
        implements BookingRepository {

    @Override
    public List<Booking> findByUserId(String userId) {
        return findMatching(booking -> booking.getUser() != null
                && userId.equals(booking.getUser().getUserId()));
    }

    @Override
    public List<Booking> findByVehicleId(String vehicleId) {
        return findMatching(booking -> booking.getVehicle() != null
                && vehicleId.equals(booking.getVehicle().getVehicleId()));
    }

    @Override
    public List<Booking> findByServiceId(String serviceId) {
        return findMatching(booking -> booking.getService() != null
                && serviceId.equals(booking.getService().getServiceId()));
    }

    @Override
    public List<Booking> findByBranchId(String branchId) {
        return findMatching(booking -> branchId != null && branchId.equals(booking.getBranchId()));
    }

    @Override
    public List<Booking> findByServiceOfferingId(String serviceOfferingId) {
        return findMatching(booking -> serviceOfferingId != null
                && serviceOfferingId.equals(booking.getServiceOfferingId()));
    }

    @Override
    public List<Booking> findByScheduledDateTime(LocalDateTime scheduledDateTime) {
        return findMatching(booking -> booking.getScheduledDateTime() != null
                && booking.getScheduledDateTime().equals(scheduledDateTime));
    }

    @Override
    public boolean existsByUserId(String userId) {
        return anyMatch(booking -> booking.getUser() != null
                && userId.equals(booking.getUser().getUserId()));
    }

    @Override
    public boolean existsByVehicleId(String vehicleId) {
        return anyMatch(booking -> booking.getVehicle() != null
                && vehicleId.equals(booking.getVehicle().getVehicleId()));
    }

    @Override
    public boolean existsByServiceId(String serviceId) {
        return anyMatch(booking -> booking.getService() != null
                && serviceId.equals(booking.getService().getServiceId()));
    }

    @Override
    public boolean existsByServiceOfferingId(String serviceOfferingId) {
        return anyMatch(booking -> serviceOfferingId != null
                && serviceOfferingId.equals(booking.getServiceOfferingId()));
    }

    @Override
    protected String getId(Booking entity) {
        return entity.getBookingId();
    }
}
