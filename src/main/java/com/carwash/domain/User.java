package com.carwash.domain;

import com.carwash.enums.AccountStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Setter
@Getter
public class User {
    private String userId;
    private String fullName;
    private String email;
    private String phone;
    @JsonIgnore
    private String passwordHash;
    private AccountStatus accountStatus;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private Role role;
    private List<Vehicle> vehicles = new ArrayList<>();
    private List<Booking> bookings = new ArrayList<>();
    private List<Notification> notifications = new ArrayList<>();

    public User() {
    }

    public User(String userId, String fullName, String email, String phone, String passwordHash, Role role) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role;
        this.accountStatus = AccountStatus.PENDING;
    }

    public void registerAccount() {
        this.accountStatus = AccountStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    public boolean authenticate(String suppliedPasswordHash) {
        boolean authenticated = this.accountStatus == AccountStatus.ACTIVE && passwordHash != null && passwordHash.equals(suppliedPasswordHash);
        if (authenticated) {
            this.lastLoginAt = LocalDateTime.now();
        }
        return authenticated;
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
                .filter(q -> q != null)
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
