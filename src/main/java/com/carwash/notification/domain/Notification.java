package com.carwash.notification.domain;

import com.carwash.booking.domain.Booking;
import com.carwash.identity.domain.User;

import com.carwash.notification.domain.DeliveryStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Setter
@Getter
public class Notification {
    private String notificationId;
    private User user;
    private Booking booking;
    private String branchId;
    private String serviceOfferingId;
    private String type;
    private String message;
    private String channel;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private DeliveryStatus deliveryStatus;

    public Notification() {
    }

    public Notification(String notificationId, User user, Booking booking, String type, String message, String channel) {
        this.notificationId = notificationId;
        this.user = user;
        this.booking = booking;
        this.branchId = booking == null ? null : booking.getBranchId();
        this.serviceOfferingId = booking == null ? null : booking.getServiceOfferingId();
        this.type = type;
        this.message = message;
        this.channel = channel;
        this.deliveryStatus = DeliveryStatus.PENDING;
    }

    public void send(LocalDateTime now) {
        this.sentAt = Objects.requireNonNull(now, "Notification sent time is required");
        this.deliveryStatus = DeliveryStatus.SENT;
    }

    /**
     * Applies the only supported public read-state transition.
     *
     * @return {@code true} when this call performed {@code SENT -> READ}, or
     * {@code false} when the notification was already read. An already-read
     * notification deliberately retains its original timestamp.
     */
    public boolean markAsRead(LocalDateTime now) {
        LocalDateTime requestedReadAt = Objects.requireNonNull(now, "Notification read time is required");
        if (deliveryStatus == DeliveryStatus.READ) {
            if (readAt == null) {
                throw new IllegalStateException("A read notification must have a read timestamp");
            }
            return false;
        }
        if (deliveryStatus != DeliveryStatus.SENT || readAt != null) {
            throw new IllegalStateException("Only a sent notification can be marked as read");
        }
        this.readAt = requestedReadAt;
        this.deliveryStatus = DeliveryStatus.READ;
        return true;
    }

    public void retryDelivery(LocalDateTime now) {
        if (deliveryStatus == DeliveryStatus.FAILED || deliveryStatus == DeliveryStatus.PENDING) {
            send(now);
        }
    }

    public String formatMessage() {
        String bookingRef = booking != null ? booking.getBookingId() : "N/A";
        return "[" + type + "] Booking " + bookingRef + ": " + message;
    }

}
