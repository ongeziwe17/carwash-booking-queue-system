package com.carwash.booking.application;

import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.booking.domain.Booking;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.shared.exception.BusinessRuleViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Owns exact-start scheduling rules for legacy availability and branch-scoped booking commands.
 * Callers are responsible for invoking this service inside the appropriate coordinator boundary.
 */
public final class BookingSlotPolicyService {

    private final BookingRepository bookingRepository;
    private final BookingPolicyProperties bookingPolicy;
    private final Clock clock;

    public BookingSlotPolicyService(
            BookingRepository bookingRepository,
            BookingPolicyProperties bookingPolicy,
            Clock clock
    ) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "Booking repository is required");
        this.bookingPolicy = Objects.requireNonNull(bookingPolicy, "Booking policy is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
    }

    public void validateBookableSlot(
            String excludedBookingId,
            LocalDateTime scheduledDateTime,
            Service service,
            User user,
            Vehicle vehicle
    ) {
        requireFutureStart(scheduledDateTime);
        validateOperatingCompatibility(scheduledDateTime, service);

        List<Booking> bookingsInSlot = activeBookingsAt(scheduledDateTime, excludedBookingId, null);
        boolean conflict = bookingsInSlot.stream()
                .anyMatch(existingBooking -> hasSameCustomerAndVehicle(existingBooking, user, vehicle));
        if (conflict) {
            throw new BusinessRuleViolationException(
                    "Customer vehicle already has an active booking for this scheduled date/time");
        }
        if (bookingsInSlot.size() >= bookingPolicy.maxActiveBookingsPerSlot()) {
            throw new BusinessRuleViolationException("Booking time slot is already full");
        }
    }

    /** Booking-specific conflict rule kept separate from public branch availability. */
    public void validateCustomerVehicleConflict(
            String excludedBookingId,
            LocalDateTime scheduledDateTime,
            String branchId,
            User user,
            Vehicle vehicle
    ) {
        if (scheduledDateTime == null) {
            throw new BusinessRuleViolationException("Scheduled date/time is required");
        }
        if (branchId == null || branchId.isBlank()) {
            throw new BusinessRuleViolationException("Branch ID is required for operational booking validation");
        }
        boolean conflict = activeBookingsAt(scheduledDateTime, excludedBookingId, branchId).stream()
                .anyMatch(existingBooking -> hasSameCustomerAndVehicle(existingBooking, user, vehicle));
        if (conflict) {
            throw new BusinessRuleViolationException(
                    "Customer vehicle already has an active booking for this scheduled date/time");
        }
    }

    public void validateBookableSlot(
            String excludedBookingId,
            LocalDateTime scheduledDateTime,
            int estimatedDurationMin,
            String branchId,
            User user,
            Vehicle vehicle
    ) {
        requireFutureStart(scheduledDateTime);
        validateOperatingCompatibility(scheduledDateTime, estimatedDurationMin);
        if (branchId == null || branchId.isBlank()) {
            throw new BusinessRuleViolationException("Branch ID is required for operational booking validation");
        }

        List<Booking> bookingsInSlot = activeBookingsAt(scheduledDateTime, excludedBookingId, branchId);
        boolean conflict = bookingsInSlot.stream()
                .anyMatch(existingBooking -> hasSameCustomerAndVehicle(existingBooking, user, vehicle));
        if (conflict) {
            throw new BusinessRuleViolationException(
                    "Customer vehicle already has an active booking for this scheduled date/time");
        }
        if (bookingsInSlot.size() >= bookingPolicy.maxActiveBookingsPerSlot()) {
            throw new BusinessRuleViolationException("Booking time slot is already full");
        }
    }

    public List<AvailableSlot> findAvailableSlots(LocalDate date, Service service) {
        requireCurrentOrFutureDate(date);
        requireService(service);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime opening = date.atTime(bookingPolicy.operatingStart());
        LocalDateTime closing = date.atTime(bookingPolicy.operatingEnd());
        List<AvailableSlot> slots = new ArrayList<>();

        for (LocalDateTime start = opening; start.isBefore(closing); start = start.plus(bookingPolicy.slotInterval())) {
            LocalDateTime estimatedEnd = service.calculateEstimatedEndTime(start);
            if (estimatedEnd.isAfter(closing)) {
                break;
            }
            if (!start.isAfter(now)) {
                continue;
            }
            Capacity capacity = capacityAt(start, null);
            if (!capacity.full()) {
                slots.add(new AvailableSlot(start, estimatedEnd, capacity.remaining()));
            }
        }
        return List.copyOf(slots);
    }

    public void validateOperatingCompatibility(LocalDateTime scheduledDateTime, Service service) {
        if (scheduledDateTime == null) {
            throw new BusinessRuleViolationException("Scheduled date/time is required");
        }
        requireService(service);

        LocalDateTime opening = scheduledDateTime.toLocalDate().atTime(bookingPolicy.operatingStart());
        LocalDateTime closing = scheduledDateTime.toLocalDate().atTime(bookingPolicy.operatingEnd());
        if (scheduledDateTime.isBefore(opening) || !scheduledDateTime.isBefore(closing)) {
            throw new BusinessRuleViolationException("Scheduled date/time is outside operating hours");
        }

        Duration sinceOpening = Duration.between(opening, scheduledDateTime);
        long intervalSeconds = bookingPolicy.slotInterval().getSeconds();
        if (sinceOpening.getNano() != 0 || sinceOpening.getSeconds() % intervalSeconds != 0) {
            throw new BusinessRuleViolationException("Scheduled date/time must align with the configured slot interval");
        }

        if (service.calculateEstimatedEndTime(scheduledDateTime).isAfter(closing)) {
            throw new BusinessRuleViolationException("Selected service does not fit within operating hours");
        }
    }

    public void validateOperatingCompatibility(LocalDateTime scheduledDateTime, int estimatedDurationMin) {
        if (estimatedDurationMin <= 0) {
            throw new BusinessRuleViolationException("Service offering duration must be positive");
        }
        Service durationProjection = new Service();
        durationProjection.setEstimatedDurationMin(estimatedDurationMin);
        validateOperatingCompatibility(scheduledDateTime, durationProjection);
    }

    private void requireFutureStart(LocalDateTime scheduledDateTime) {
        if (scheduledDateTime == null || !scheduledDateTime.isAfter(LocalDateTime.now(clock))) {
            throw new BusinessRuleViolationException("Scheduled date/time must be in the future");
        }
    }

    private void requireCurrentOrFutureDate(LocalDate date) {
        if (date == null) {
            throw new BusinessRuleViolationException("Availability date is required");
        }
        if (date.isBefore(LocalDate.now(clock))) {
            throw new BusinessRuleViolationException("Availability date must be today or in the future");
        }
    }

    private void requireService(Service service) {
        if (service == null) {
            throw new BusinessRuleViolationException("Service is required");
        }
        if (service.getEstimatedDurationMin() <= 0) {
            throw new BusinessRuleViolationException("Service duration must be positive");
        }
    }

    private Capacity capacityAt(LocalDateTime scheduledDateTime, String excludedBookingId) {
        int activeCount = activeBookingsAt(scheduledDateTime, excludedBookingId, null).size();
        int remaining = Math.max(0, bookingPolicy.maxActiveBookingsPerSlot() - activeCount);
        return new Capacity(remaining, remaining == 0);
    }

    private List<Booking> activeBookingsAt(
            LocalDateTime scheduledDateTime,
            String excludedBookingId,
            String branchId
    ) {
        return bookingRepository.findByScheduledDateTime(scheduledDateTime).stream()
                .filter(booking -> !isSameBooking(booking, excludedBookingId))
                .filter(booking -> branchId == null || branchId.equals(booking.getBranchId()))
                .filter(this::occupiesCapacity)
                .toList();
    }

    private boolean occupiesCapacity(Booking booking) {
        return booking.getStatus() != BookingStatus.CANCELLED;
    }

    private boolean isSameBooking(Booking booking, String excludedBookingId) {
        return excludedBookingId != null
                && booking.getBookingId() != null
                && booking.getBookingId().equals(excludedBookingId);
    }

    private boolean hasSameCustomerAndVehicle(Booking booking, User user, Vehicle vehicle) {
        return booking.getUser() != null && booking.getVehicle() != null && user != null && vehicle != null
                && Objects.equals(booking.getUser().getUserId(), user.getUserId())
                && Objects.equals(booking.getVehicle().getVehicleId(), vehicle.getVehicleId());
    }

    public record AvailableSlot(
            LocalDateTime startDateTime,
            LocalDateTime estimatedEndDateTime,
            int capacityRemaining
    ) {
    }

    private record Capacity(int remaining, boolean full) {
    }
}
