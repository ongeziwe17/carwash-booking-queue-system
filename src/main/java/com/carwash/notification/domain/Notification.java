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
        this.type = type;
        this.message = message;
        this.channel = channel;
        this.deliveryStatus = DeliveryStatus.PENDING;
    }

    public void send(LocalDateTime now) {
        this.sentAt = Objects.requireNonNull(now, "Notification sent time is required");
        this.deliveryStatus = DeliveryStatus.SENT;
    }

    public void markAsRead(LocalDateTime now) {
        this.readAt = Objects.requireNonNull(now, "Notification read time is required");
        this.deliveryStatus = DeliveryStatus.READ;
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
