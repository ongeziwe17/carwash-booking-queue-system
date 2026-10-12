package com.carwash.booking.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;

import com.carwash.booking.domain.Booking;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.marketplace.application.MarketplaceQuery;

import java.time.LocalDateTime;
import java.util.List;

public class InMemoryBookingRepository extends InMemoryRepository<Booking, String>
        implements BookingRepository {
    @Override public com.carwash.shared.application.PageResult<Booking> findPage(com.carwash.shared.application.ListQuery query) {
        var scope = query.scope();
        java.util.List<Booking> source = scope.businessId() == null ? findMatching(v -> true) : findByBusinessId(scope.businessId());
        source = source.stream().filter(v -> scope.branchId() == null || scope.branchId().equals(v.getBranchId())).toList();
        return query.select(source, java.util.Map.ofEntries(java.util.Map.entry("id", (java.util.function.Function<Booking, Object>) v -> v.getBookingId()),
                java.util.Map.entry("scheduledAt", (java.util.function.Function<Booking, Object>) v -> v.getScheduledDateTime()),
                java.util.Map.entry("createdAt", (java.util.function.Function<Booking, Object>) v -> v.getCreatedAt()),
                java.util.Map.entry("status", (java.util.function.Function<Booking, Object>) v -> v.getStatus()),
                java.util.Map.entry("userId", (java.util.function.Function<Booking, Object>) v -> v.getUser().getUserId()),
                java.util.Map.entry("serviceId", (java.util.function.Function<Booking, Object>) v -> v.getService().getServiceId()),
                java.util.Map.entry("offeringId", (java.util.function.Function<Booking, Object>) v -> v.getServiceOfferingId()),
                java.util.Map.entry("from", (java.util.function.Function<Booking, Object>) v -> v.getScheduledDateTime()),
                java.util.Map.entry("to", (java.util.function.Function<Booking, Object>) v -> v.getScheduledDateTime())));
    }


    private final MarketplaceQuery marketplace;

    public InMemoryBookingRepository() {
        this(null);
    }

    public InMemoryBookingRepository(MarketplaceQuery marketplace) {
        this.marketplace = marketplace;
    }

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
    public List<Booking> findByBusinessId(String businessId) {
        return findMatching(booking -> branchBelongsTo(booking.getBranchId(), businessId));
    }

    @Override
    public List<Booking> findByBranchIdAndBusinessId(String branchId, String businessId) {
        if (!branchBelongsTo(branchId, businessId)) return List.of();
        return findByBranchId(branchId);
    }

    @Override
    public List<Booking> findByUserIdAndBusinessId(String userId, String businessId) {
        return findMatching(booking -> booking.getUser() != null
                && userId.equals(booking.getUser().getUserId())
                && branchBelongsTo(booking.getBranchId(), businessId));
    }

    @Override
    public java.util.Optional<Booking> findByIdAndBusinessId(String bookingId, String businessId) {
        return findById(bookingId).filter(booking -> branchBelongsTo(booking.getBranchId(), businessId));
    }

    @Override
    public java.util.Optional<Booking> findByIdAndUserId(String bookingId, String userId) {
        return findById(bookingId).filter(booking -> booking.getUser() != null
                && userId.equals(booking.getUser().getUserId()));
    }

    @Override
    public boolean updateForBusiness(Booking booking, String businessId) {
        return updateMatching(booking.getBookingId(), booking,
                current -> branchBelongsTo(current.getBranchId(), businessId));
    }

    @Override
    public boolean updateForUser(Booking booking, String userId) {
        return updateMatching(booking.getBookingId(), booking,
                current -> current.getUser() != null && userId.equals(current.getUser().getUserId()));
    }

    @Override
    public boolean updateForAdministrator(Booking booking) {
        return updateMatching(booking.getBookingId(), booking, current -> true);
    }

    @Override
    public boolean deleteForBusiness(String bookingId, String businessId) {
        return deleteMatching(bookingId, current -> branchBelongsTo(current.getBranchId(), businessId));
    }

    @Override
    public boolean deleteForUser(String bookingId, String userId) {
        return deleteMatching(bookingId,
                current -> current.getUser() != null && userId.equals(current.getUser().getUserId()));
    }

    @Override
    public boolean deleteForAdministrator(String bookingId) {
        return deleteMatching(bookingId, current -> true);
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

    private boolean branchBelongsTo(String branchId, String businessId) {
        return marketplace != null && marketplace.findBranchOptional(branchId)
                .filter(branch -> businessId.equals(branch.businessId())).isPresent();
    }
}
