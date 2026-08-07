package com.carwash.domain;

import com.carwash.enums.AccountStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
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
    @Setter(AccessLevel.NONE)
    private final List<Vehicle> vehicles = new ArrayList<>();
    @Setter(AccessLevel.NONE)
    private final List<Booking> bookings = new ArrayList<>();
    @Setter(AccessLevel.NONE)
    private final List<Notification> notifications = new ArrayList<>();

    public User() {
    }

    private User(
            String userId,
            String fullName,
            String email,
            String phone,
            String encodedPassword,
            Role role
    ) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.encodedPassword = encodedPassword;
        this.role = role;
        this.accountStatus = AccountStatus.PENDING;
    }

    public static User withEncodedPassword(
            String userId,
            String fullName,
            String email,
            String phone,
            String encodedPassword,
            Role role
    ) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException("Encoded password is required");
        }
        return new User(userId, fullName, email, phone, encodedPassword, role);
    }

    @JsonIgnore
    public List<Vehicle> getVehicles() {
        return List.copyOf(vehicles);
    }

    @JsonIgnore
    public List<Booking> getBookings() {
        return List.copyOf(bookings);
    }

    @JsonIgnore
    public List<Notification> getNotifications() {
        return List.copyOf(notifications);
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

    public void addVehicle(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "Vehicle is required");
        requireStableId(vehicle.getVehicleId(), "Vehicle ID is required");
        if (vehicles.stream().noneMatch(existing -> vehicle.getVehicleId().equals(existing.getVehicleId()))) {
            vehicles.add(vehicle);
        }
    }

    public boolean removeVehicle(String vehicleId) {
        requireStableId(vehicleId, "Vehicle ID is required");
        return vehicles.removeIf(vehicle -> vehicleId.equals(vehicle.getVehicleId()));
    }

    public void addBooking(Booking booking) {
        Objects.requireNonNull(booking, "Booking is required");
        requireStableId(booking.getBookingId(), "Booking ID is required");
        if (bookings.stream().noneMatch(existing -> booking.getBookingId().equals(existing.getBookingId()))) {
            bookings.add(booking);
        }
    }

    public boolean removeBooking(String bookingId) {
        requireStableId(bookingId, "Booking ID is required");
        return bookings.removeIf(booking -> bookingId.equals(booking.getBookingId()));
    }

    public void addNotification(Notification notification) {
        Objects.requireNonNull(notification, "Notification is required");
        requireStableId(notification.getNotificationId(), "Notification ID is required");
        if (notifications.stream().noneMatch(
                existing -> notification.getNotificationId().equals(existing.getNotificationId()))) {
            notifications.add(notification);
        }
    }

    public boolean removeNotification(String notificationId) {
        requireStableId(notificationId, "Notification ID is required");
        return notifications.removeIf(
                notification -> notificationId.equals(notification.getNotificationId()));
    }

    public void clearNotifications() {
        notifications.clear();
    }

    public Booking createBooking(
            String bookingId,
            Vehicle vehicle,
            Service service,
            LocalDateTime scheduledDateTime,
            String specialRequest
    ) {
        Booking booking = Booking.create(
                bookingId,
                this,
                vehicle,
                service,
                scheduledDateTime,
                specialRequest
        );
        addBooking(booking);
        return booking;
    }

    public boolean cancelBooking(String bookingId) {
        Optional<Booking> booking = bookings.stream()
                .filter(item -> item.getBookingId().equals(bookingId))
                .findFirst();
        return booking.map(Booking::cancel).orElse(false);
    }

    public int viewQueuePosition(String bookingId) {
        return bookings.stream()
                .filter(booking -> booking.getBookingId().equals(bookingId))
                .map(Booking::getQueueEntry)
                .filter(Objects::nonNull)
                .map(QueueEntry::getPosition)
                .findFirst()
                .orElse(-1);
    }

    public boolean markNotificationAsRead(String notificationId) {
        return notifications.stream()
                .filter(notification -> notification.getNotificationId().equals(notificationId))
                .findFirst()
                .map(notification -> {
                    notification.markAsRead();
                    return true;
                })
                .orElse(false);
    }

    private void requireStableId(String id, String message) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
