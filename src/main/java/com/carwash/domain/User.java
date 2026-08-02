package com.carwash.domain;

import com.carwash.enums.AccountStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Getter
public class User {
    @Setter
    private String userId;
    @Setter
    private String fullName;
    @Setter
    private String email;
    @Setter
    private String phone;
    @JsonIgnore
    private String encodedPassword;
    @Setter
    private AccountStatus accountStatus;
    @Setter
    private LocalDateTime createdAt;
    @Setter
    private LocalDateTime lastLoginAt;
    @Setter
    private Role role;
    @Setter
    private List<Vehicle> vehicles = new ArrayList<>();
    @Setter
    private List<Booking> bookings = new ArrayList<>();
    @Setter
    private List<Notification> notifications = new ArrayList<>();

    public User() {
    }

    private User(String userId, String fullName, String email, String phone, String encodedPassword, Role role) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.encodedPassword = encodedPassword;
        this.role = role;
        this.accountStatus = AccountStatus.PENDING;
    }

    public static User withEncodedPassword(String userId, String fullName, String email, String phone,
                                           String encodedPassword, Role role) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException("Encoded password is required");
        }
        return new User(userId, fullName, email, phone, encodedPassword, role);
    }

    public void registerAccount() {
        this.accountStatus = AccountStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    public void recordSuccessfulLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void updateProfile(String fullName, String email, String phone) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
    }

    public Booking createBooking(String bookingId, Vehicle vehicle, Service service, LocalDateTime scheduledDateTime, String specialRequest) {
        Booking booking = Booking.create(bookingId, this, vehicle, service, scheduledDateTime, specialRequest);
        this.bookings.add(booking);
        return booking;
    }

    public boolean cancelBooking(String bookingId) {
        Optional<Booking> booking = bookings.stream().filter(b -> b.getBookingId().equals(bookingId)).findFirst();
        return booking.map(Booking::cancel).orElse(false);
    }

    public int viewQueuePosition(String bookingId) {
        return bookings.stream()
                .filter(b -> b.getBookingId().equals(bookingId))
                .map(Booking::getQueueEntry)
                .filter(Objects::nonNull)
                .map(QueueEntry::getPosition)
                .findFirst()
                .orElse(-1);
    }

    public boolean markNotificationAsRead(String notificationId) {
        return notifications.stream()
                .filter(n -> n.getNotificationId().equals(notificationId))
                .findFirst()
                .map(n -> {
                    n.markAsRead();
                    return true;
                }).orElse(false);
    }

}
