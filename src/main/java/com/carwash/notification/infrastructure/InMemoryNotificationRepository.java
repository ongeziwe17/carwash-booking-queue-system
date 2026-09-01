package com.carwash.notification.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;
import com.carwash.marketplace.application.MarketplaceQuery;

import com.carwash.notification.domain.Notification;
import com.carwash.notification.domain.NotificationRepository;

import java.util.List;

public class InMemoryNotificationRepository extends InMemoryRepository<Notification, String>
        implements NotificationRepository {

    private final MarketplaceQuery marketplace;

    public InMemoryNotificationRepository() {
        this(null);
    }

    public InMemoryNotificationRepository(MarketplaceQuery marketplace) {
        this.marketplace = marketplace;
    }

    @Override
    public List<Notification> findByUserId(String userId) {
        return findMatching(notification -> notification.getUser() != null
                && userId.equals(notification.getUser().getUserId()));
    }

    @Override
    public List<Notification> findByBookingId(String bookingId) {
        return findMatching(notification -> notification.getBooking() != null
                && bookingId.equals(notification.getBooking().getBookingId()));
    }

    @Override
    public List<Notification> findByUserIdAndBusinessId(String userId, String businessId) {
        return findMatching(notification -> notification.getUser() != null
                && userId.equals(notification.getUser().getUserId())
                && branchBelongsTo(notification.getBranchId(), businessId));
    }

    @Override
    public List<Notification> findByBusinessId(String businessId) {
        return findMatching(notification -> branchBelongsTo(notification.getBranchId(), businessId));
    }

    @Override
    public java.util.Optional<Notification> findByIdAndBusinessId(String notificationId, String businessId) {
        return findById(notificationId)
                .filter(notification -> branchBelongsTo(notification.getBranchId(), businessId));
    }

    @Override
    public int deleteByUserId(String userId) {
        return deleteMatching(notification -> notification.getUser() != null
                && userId.equals(notification.getUser().getUserId()));
    }

    @Override
    public int deleteByBookingId(String bookingId) {
        return deleteMatching(notification -> notification.getBooking() != null
                && bookingId.equals(notification.getBooking().getBookingId()));
    }

    @Override
    public int deleteByBookingIdAndBusinessId(String bookingId, String businessId) {
        return deleteMatching(notification -> notification.getBooking() != null
                && bookingId.equals(notification.getBooking().getBookingId())
                && branchBelongsTo(notification.getBranchId(), businessId));
    }

    @Override
    public int deleteByBookingIdAndUserId(String bookingId, String userId) {
        return deleteMatching(notification -> notification.getBooking() != null
                && bookingId.equals(notification.getBooking().getBookingId())
                && notification.getUser() != null && userId.equals(notification.getUser().getUserId()));
    }

    @Override
    public int deleteByBookingIdForAdministrator(String bookingId) {
        return deleteByBookingId(bookingId);
    }

    @Override
    protected String getId(Notification entity) {
        return entity.getNotificationId();
    }

    private boolean branchBelongsTo(String branchId, String businessId) {
        return marketplace != null && marketplace.findBranchOptional(branchId)
                .filter(branch -> businessId.equals(branch.businessId())).isPresent();
    }
}
