package com.carwash.booking.domain;

import com.carwash.shared.domain.Repository;

import com.carwash.booking.domain.Booking;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends Repository<Booking, String> {
    default List<Booking> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream().filter(java.util.Objects::nonNull).distinct().sorted()
                .map(this::findById).flatMap(java.util.Optional::stream).toList();
    }

    List<Booking> findByUserId(String userId);
    List<Booking> findByVehicleId(String vehicleId);
    List<Booking> findByServiceId(String serviceId);
    List<Booking> findByBranchId(String branchId);
    List<Booking> findByServiceOfferingId(String serviceOfferingId);
    List<Booking> findByScheduledDateTime(LocalDateTime scheduledDateTime);

    boolean existsByUserId(String userId);
    boolean existsByVehicleId(String vehicleId);
    boolean existsByServiceId(String serviceId);
    boolean existsByServiceOfferingId(String serviceOfferingId);
}
