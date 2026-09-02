package com.carwash.notification.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;
import com.carwash.marketplace.application.MarketplaceQuery;

import com.carwash.notification.domain.Notification;
import com.carwash.notification.domain.NotificationCursor;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.notification.domain.NotificationSnapshot;
import com.carwash.notification.domain.DeliveryStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
    public boolean insert(Notification notification) {
        validateReadState(notification);
        return super.insert(notification);
    }

    @Override
    public boolean update(Notification notification) {
        validateReadState(notification);
        return super.update(notification);
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
    public List<NotificationSnapshot> findPageByUserId(
            String userId, boolean unreadOnly, NotificationCursor cursor, int limit) {
        return page(findMatching(notification -> belongsToUser(notification, userId)
                && (!unreadOnly || notification.getDeliveryStatus() == DeliveryStatus.SENT)), cursor, limit);
    }

    @Override
    public List<NotificationSnapshot> findPageByUserIdAndBusinessId(
            String userId, String businessId, boolean unreadOnly, NotificationCursor cursor, int limit) {
        return page(findMatching(notification -> belongsToUser(notification, userId)
                && branchBelongsTo(notification.getBranchId(), businessId)
                && (!unreadOnly || notification.getDeliveryStatus() == DeliveryStatus.SENT)), cursor, limit);
    }

    @Override
    public long countUnreadByUserId(String userId) {
        return findMatching(notification -> belongsToUser(notification, userId)
                && notification.getDeliveryStatus() == DeliveryStatus.SENT).size();
    }

    @Override
    public long countUnreadByUserIdAndBusinessId(String userId, String businessId) {
        return findMatching(notification -> belongsToUser(notification, userId)
                && branchBelongsTo(notification.getBranchId(), businessId)
                && notification.getDeliveryStatus() == DeliveryStatus.SENT).size();
    }

    @Override
    public Optional<NotificationSnapshot> findSnapshotByIdAndUserId(String notificationId, String userId) {
        return findById(notificationId).filter(notification -> belongsToUser(notification, userId))
                .map(InMemoryNotificationRepository::snapshot);
    }

    @Override
    public Optional<NotificationSnapshot> markAsReadByUserId(
            String notificationId, String userId, LocalDateTime readAt) {
        return mutateMatching(notificationId,
                notification -> belongsToUser(notification, userId),
                notification -> {
                    if (notification.getDeliveryStatus() == DeliveryStatus.SENT) {
                        notification.markAsRead(readAt);
                    }
                    return snapshot(notification);
                });
    }

    @Override
    public int markAllAsReadByUserId(String userId, LocalDateTime readAt) {
        return mutateMatching(notification -> belongsToUser(notification, userId)
                        && notification.getDeliveryStatus() == DeliveryStatus.SENT,
                notification -> notification.markAsRead(readAt));
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

    private static boolean belongsToUser(Notification notification, String userId) {
        return notification.getUser() != null && userId.equals(notification.getUser().getUserId());
    }

    private static List<NotificationSnapshot> page(
            List<Notification> notifications, NotificationCursor cursor, int limit) {
        return notifications.stream()
                .filter(notification -> notification.getSentAt() != null)
                .filter(notification -> beforeCursor(notification, cursor))
                .sorted(Comparator.comparing(Notification::getSentAt)
                        .thenComparing(Notification::getNotificationId).reversed())
                .limit(limit)
                .map(InMemoryNotificationRepository::snapshot)
                .toList();
    }

    private static boolean beforeCursor(Notification notification, NotificationCursor cursor) {
        if (cursor == null) return true;
        int time = notification.getSentAt().compareTo(cursor.sentAt());
        return time < 0 || (time == 0
                && notification.getNotificationId().compareTo(cursor.notificationId()) < 0);
    }

    private static NotificationSnapshot snapshot(Notification notification) {
        return new NotificationSnapshot(
                notification.getNotificationId(),
                notification.getUser() == null ? null : notification.getUser().getUserId(),
                notification.getBooking() == null ? null : notification.getBooking().getBookingId(),
                notification.getBranchId(),
                notification.getServiceOfferingId(),
                notification.getType(),
                notification.getMessage(),
                notification.getChannel(),
                notification.getSentAt(),
                notification.getReadAt(),
                notification.getDeliveryStatus());
    }

    private static void validateReadState(Notification notification) {
        if (notification == null || notification.getDeliveryStatus() == null) {
            throw new IllegalArgumentException("Notification delivery status is required");
        }
        boolean read = notification.getDeliveryStatus() == DeliveryStatus.READ;
        if (read != (notification.getReadAt() != null)) {
            throw new IllegalArgumentException("Notification read state is inconsistent");
        }
    }
}
