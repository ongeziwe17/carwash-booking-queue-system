package com.carwash.booking.domain;

import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.User;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.vehicle.domain.Vehicle;

import com.carwash.booking.domain.BookingStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Setter
@Getter
public class Booking {
    private String bookingId;
    private User user;
    private Vehicle vehicle;
    private Service service;
    @Setter(AccessLevel.NONE)
    private String branchId;
    @Setter(AccessLevel.NONE)
    private String serviceOfferingId;
    private LocalDateTime scheduledDateTime;
    private BookingStatus status;
    private LocalDateTime createdAt;
    private String specialRequest;
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private QueueEntry queueEntry;

    public Booking() {
    }

    private Booking(
            String bookingId,
            User user,
            Vehicle vehicle,
            Service service,
            LocalDateTime scheduledDateTime,
            String specialRequest
    ) {
        this.bookingId = bookingId;
        this.user = user;
        this.vehicle = vehicle;
        this.service = service;
        this.scheduledDateTime = scheduledDateTime;
        this.specialRequest = specialRequest;
        this.status = BookingStatus.CREATED;
        this.createdAt = LocalDateTime.now();
    }

    public Booking(
            String bookingId,
            User user,
            Vehicle vehicle,
            String branchId,
            String serviceOfferingId,
            Service service,
            LocalDateTime scheduledDateTime,
            String specialRequest
    ) {
        this(bookingId, user, vehicle, service, scheduledDateTime, specialRequest);
        assignOperationalScope(branchId, serviceOfferingId);
    }

    public static Booking create(
            String bookingId,
            User user,
            Vehicle vehicle,
            String branchId,
            String serviceOfferingId,
            Service service,
            LocalDateTime scheduledDateTime,
            String specialRequest
    ) {
        return new Booking(
                bookingId, user, vehicle, branchId, serviceOfferingId, service, scheduledDateTime, specialRequest);
    }

    public void assignOperationalScope(String branchId, String serviceOfferingId) {
        String normalizedBranchId = requireId(branchId, "Branch ID");
        String normalizedOfferingId = requireId(serviceOfferingId, "Service offering ID");
        if (this.branchId != null && !this.branchId.equals(normalizedBranchId)) {
            throw new IllegalStateException("Booking branch is immutable");
        }
        if (this.serviceOfferingId != null && !this.serviceOfferingId.equals(normalizedOfferingId)) {
            throw new IllegalStateException("Use changeServiceOffering to replace a booking offering");
        }
        this.branchId = normalizedBranchId;
        this.serviceOfferingId = normalizedOfferingId;
    }

    public void changeServiceOffering(String serviceOfferingId, Service service) {
        if (branchId == null) {
            throw new IllegalStateException("Booking branch is required before changing its offering");
        }
        this.serviceOfferingId = requireId(serviceOfferingId, "Service offering ID");
        this.service = Objects.requireNonNull(service, "Service is required");
    }

    public void attachQueueEntry(QueueEntry queueEntry) {
        Objects.requireNonNull(queueEntry, "Queue entry is required");
        if (queueEntry.getQueueEntryId() == null || queueEntry.getQueueEntryId().isBlank()) {
            throw new IllegalArgumentException("Queue entry ID is required");
        }
        if (this.queueEntry != null
                && !queueEntry.getQueueEntryId().equals(this.queueEntry.getQueueEntryId())) {
            throw new IllegalStateException("Booking already has a queue entry");
        }
        this.queueEntry = queueEntry;
    }

    public boolean detachQueueEntry(String queueEntryId) {
        if (queueEntryId == null || queueEntryId.isBlank()) {
            throw new IllegalArgumentException("Queue entry ID is required");
        }
        if (queueEntry != null && queueEntryId.equals(queueEntry.getQueueEntryId())) {
            queueEntry = null;
            return true;
        }
        return false;
    }

    public boolean confirm() {
        if (validateStatusTransition(BookingStatus.CONFIRMED)) {
            return false;
        }
        this.status = BookingStatus.CONFIRMED;
        return true;
    }

    public boolean cancel() {
        if (validateStatusTransition(BookingStatus.CANCELLED)) {
            return false;
        }
        this.status = BookingStatus.CANCELLED;
        return true;
    }

    public boolean startService() {
        if (validateStatusTransition(BookingStatus.IN_SERVICE)) {
            return false;
        }
        this.status = BookingStatus.IN_SERVICE;
        return true;
    }

    public boolean completeService() {
        if (validateStatusTransition(BookingStatus.COMPLETED)) {
            return false;
        }
        this.status = BookingStatus.COMPLETED;
        return true;
    }

    public boolean validateStatusTransition(BookingStatus targetStatus) {
        if (targetStatus == null || status == null) {
            return true;
        }
        return !switch (status) {
            case CREATED ->
                    targetStatus == BookingStatus.CONFIRMED
                            || targetStatus == BookingStatus.CANCELLED;
            case CONFIRMED ->
                    targetStatus == BookingStatus.IN_SERVICE
                            || targetStatus == BookingStatus.CANCELLED;
            case IN_SERVICE -> targetStatus == BookingStatus.COMPLETED;
            case CANCELLED, COMPLETED -> false;
        };
    }

    private String requireId(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (normalized.length() > 64) {
            throw new IllegalArgumentException(field + " must not exceed 64 characters");
        }
        return normalized;
    }
}
