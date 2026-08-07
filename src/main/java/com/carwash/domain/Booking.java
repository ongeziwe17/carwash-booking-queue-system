package com.carwash.domain;

import com.carwash.enums.BookingStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class Booking {
    private String bookingId;
    private User user;
    private Vehicle vehicle;
    private Service service;
    private LocalDateTime scheduledDateTime;
    private BookingStatus status;
    private LocalDateTime createdAt;
    private String specialRequest;
    private QueueEntry queueEntry;

    public Booking() {
    }

    public Booking(String bookingId, User user, Vehicle vehicle, Service service, LocalDateTime scheduledDateTime, String specialRequest) {
        this.bookingId = bookingId;
        this.user = user;
        this.vehicle = vehicle;
        this.service = service;
        this.scheduledDateTime = scheduledDateTime;
        this.specialRequest = specialRequest;
        this.status = BookingStatus.CREATED;
        this.createdAt = LocalDateTime.now();
    }

    public static Booking create(String bookingId, User user, Vehicle vehicle, Service service, LocalDateTime scheduledDateTime, String specialRequest) {
        return new Booking(bookingId, user, vehicle, service, scheduledDateTime, specialRequest);
    }

    public boolean confirm() {
        if (validateStatusTransition(BookingStatus.CONFIRMED)) return false;
        this.status = BookingStatus.CONFIRMED;
        return true;
    }

    public boolean cancel() {
        if (validateStatusTransition(BookingStatus.CANCELLED)) return false;
        this.status = BookingStatus.CANCELLED;
        return true;
    }

    public boolean startService() {
        if (validateStatusTransition(BookingStatus.IN_SERVICE)) return false;
        this.status = BookingStatus.IN_SERVICE;
        return true;
    }

    public boolean completeService() {
        if (validateStatusTransition(BookingStatus.COMPLETED)) return false;
        this.status = BookingStatus.COMPLETED;
        return true;
    }

    public boolean validateStatusTransition(BookingStatus targetStatus) {
        if (targetStatus == null || status == null) return true;
        return !switch (status) {
            case CREATED -> targetStatus == BookingStatus.CONFIRMED || targetStatus == BookingStatus.CANCELLED;
            case CONFIRMED -> targetStatus == BookingStatus.IN_SERVICE || targetStatus == BookingStatus.CANCELLED;
            case IN_SERVICE -> targetStatus == BookingStatus.COMPLETED;
            case CANCELLED, COMPLETED -> false;
        };
    }

}
