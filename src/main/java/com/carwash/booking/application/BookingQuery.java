package com.carwash.booking.application;

import java.util.List;
import java.util.Optional;

/** Published booking read contract for reporting, authorization, and reference checks. */
public interface BookingQuery {

    List<BookingSnapshot> findBookingSnapshots();

    List<BookingSnapshot> findBookingSnapshotsByBranch(String branchId);

    List<BookingSnapshot> findBookingSnapshotsByBusiness(String businessId);

    List<BookingSnapshot> findBookingSnapshotsByBranchAndBusiness(String branchId, String businessId);

    Optional<String> findOwnerId(String bookingId);

    boolean existsByUserId(String userId);

    boolean existsByVehicleId(String vehicleId);

    boolean existsByServiceId(String serviceId);
}
