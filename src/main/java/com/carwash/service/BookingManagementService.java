package com.carwash.service;

import com.carwash.domain.Booking;
import com.carwash.domain.Service;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.enums.BookingStatus;
import com.carwash.repository.BookingRepository;
import com.carwash.repository.ServiceRepository;
import com.carwash.repository.UserRepository;
import com.carwash.repository.VehicleRepository;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.List;

public class BookingManagementService {

    private static final int MAX_ACTIVE_BOOKINGS_PER_SLOT = 1;

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceRepository serviceRepository;
    private final NotificationManagementService notificationManagementService;

    public BookingManagementService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            ServiceRepository serviceRepository
    ) {
        this(bookingRepository, userRepository, vehicleRepository, serviceRepository, null);
    }

    public BookingManagementService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            ServiceRepository serviceRepository,
            NotificationManagementService notificationManagementService
    ) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.serviceRepository = serviceRepository;
        this.notificationManagementService = notificationManagementService;
    }

    public Booking createBooking(Booking booking) {
        validateAndResolveNewBooking(booking);
        bookingRepository.save(booking);
        return booking;
    }

    public Booking findById(String bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    public List<Booking> findAll() {
        return bookingRepository.findAll();
    }

    public Booking updateBooking(
            String bookingId,
            String vehicleId,
            String serviceId,
            LocalDateTime scheduledDateTime,
            String specialRequest
    ) {
        Booking existing = findById(bookingId);
        User owner = requireExistingOwner(existing);
        Vehicle vehicle = resolveVehicle(vehicleId);
        requireVehicleOwnedBy(vehicle, owner, "Vehicle does not belong to booking owner");
        Service service = resolveService(serviceId);

        requireActiveService(service);
        requireFutureSchedule(scheduledDateTime);
        validateSlotAvailability(bookingId, scheduledDateTime, owner, vehicle);

        existing.setVehicle(vehicle);
        existing.setService(service);
        existing.setScheduledDateTime(scheduledDateTime);
        existing.setSpecialRequest(specialRequest);
        bookingRepository.save(existing);
        return existing;
    }

    public Booking cancelBooking(String bookingId, String customerId) {
        Booking booking = findById(bookingId);
        validateCancellationRequest(booking, customerId);
        if (!booking.cancel()) {
            throw new BusinessRuleViolationException("Invalid booking status transition");
        }
        bookingRepository.save(booking);
        notifyCustomer(booking, "BOOKING_CANCELLED", "Your booking has been cancelled.");
        return booking;
    }

    /** Authorization is performed by the controller before this mutation. */
    public Booking cancelBooking(String bookingId) {
        Booking booking = findById(bookingId);
        return cancelBooking(bookingId, requireExistingOwner(booking).getUserId());
    }

    public Booking confirmBooking(String bookingId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Cancelled booking cannot be confirmed");
        }
        if (!booking.confirm()) {
            throw new BusinessRuleViolationException("Invalid booking status transition");
        }
        bookingRepository.save(booking);
        notifyCustomer(booking, "BOOKING_CONFIRMED", "Your booking has been confirmed.");
        return booking;
    }

    private void validateAndResolveNewBooking(Booking booking) {
        if (booking == null) {
            throw new BusinessRuleViolationException("Booking is required");
        }
        if (booking.getUser() == null || isBlank(booking.getUser().getUserId())) {
            throw new BusinessRuleViolationException("User is required");
        }
        if (booking.getVehicle() == null || isBlank(booking.getVehicle().getVehicleId())) {
            throw new BusinessRuleViolationException("Vehicle is required");
        }
        if (booking.getService() == null || isBlank(booking.getService().getServiceId())) {
            throw new BusinessRuleViolationException("Service is required");
        }

        User user = resolveUser(booking.getUser().getUserId());
        Vehicle vehicle = resolveVehicle(booking.getVehicle().getVehicleId());
        Service service = resolveService(booking.getService().getServiceId());

        requireActiveService(service);
        requireVehicleOwnedBy(vehicle, user, "Vehicle does not belong to selected user");
        requireFutureSchedule(booking.getScheduledDateTime());
        validateSlotAvailability(
                booking.getBookingId(),
                booking.getScheduledDateTime(),
                user,
                vehicle
        );

        booking.setUser(user);
        booking.setVehicle(vehicle);
        booking.setService(service);
    }

    private User requireExistingOwner(Booking booking) {
        if (booking.getUser() == null || isBlank(booking.getUser().getUserId())) {
            throw new BusinessRuleViolationException("Booking owner is required");
        }
        return resolveUser(booking.getUser().getUserId());
    }

    private User resolveUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private Vehicle resolveVehicle(String vehicleId) {
        if (isBlank(vehicleId)) {
            throw new BusinessRuleViolationException("Vehicle ID is required");
        }
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
    }

    private Service resolveService(String serviceId) {
        if (isBlank(serviceId)) {
            throw new BusinessRuleViolationException("Service ID is required");
        }
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
    }

    private void requireVehicleOwnedBy(Vehicle vehicle, User owner, String message) {
        if (vehicle.getUserId() == null || !vehicle.getUserId().equals(owner.getUserId())) {
            throw new BusinessRuleViolationException(message);
        }
    }

    private void requireActiveService(Service service) {
        if (!service.isActive()) {
            throw new BusinessRuleViolationException("Inactive service cannot be booked");
        }
    }

    private void requireFutureSchedule(LocalDateTime scheduledDateTime) {
        if (scheduledDateTime == null || scheduledDateTime.isBefore(LocalDateTime.now())) {
            throw new BusinessRuleViolationException("Scheduled date/time cannot be in the past");
        }
    }

    private void validateCancellationRequest(Booking booking, String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new BusinessRuleViolationException("Customer ID is required to cancel booking");
        }
        if (booking.getUser() == null
                || booking.getUser().getUserId() == null
                || !booking.getUser().getUserId().equals(customerId)) {
            throw new BusinessRuleViolationException("Booking can only be cancelled by the owning customer");
        }
        if (booking.getScheduledDateTime() == null
                || booking.getScheduledDateTime().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleViolationException("Only future bookings can be cancelled");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BusinessRuleViolationException("Completed booking cannot be cancelled");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Cancelled booking cannot be cancelled again");
        }
    }

    private void validateSlotAvailability(
            String bookingId,
            LocalDateTime scheduledDateTime,
            User user,
            Vehicle vehicle
    ) {
        List<Booking> bookingsInSlot = bookingRepository.findByScheduledDateTime(scheduledDateTime).stream()
                .filter(existingBooking -> !isSameBooking(existingBooking, bookingId))
                .filter(this::isActiveBooking)
                .toList();

        boolean hasSameCustomerVehicleConflict = bookingsInSlot.stream()
                .anyMatch(existingBooking -> hasSameCustomerAndVehicle(existingBooking, user, vehicle));
        if (hasSameCustomerVehicleConflict) {
            throw new BusinessRuleViolationException(
                    "Customer vehicle already has an active booking for this scheduled date/time"
            );
        }

        if (bookingsInSlot.size() >= MAX_ACTIVE_BOOKINGS_PER_SLOT) {
            throw new BusinessRuleViolationException("Booking time slot is already full");
        }
    }

    private boolean isActiveBooking(Booking booking) {
        return booking.getStatus() != BookingStatus.CANCELLED;
    }

    private boolean isSameBooking(Booking existingBooking, String bookingId) {
        return existingBooking.getBookingId() != null
                && existingBooking.getBookingId().equals(bookingId);
    }

    private boolean hasSameCustomerAndVehicle(Booking existingBooking, User user, Vehicle vehicle) {
        return existingBooking.getUser() != null
                && existingBooking.getVehicle() != null
                && existingBooking.getUser().getUserId().equals(user.getUserId())
                && existingBooking.getVehicle().getVehicleId().equals(vehicle.getVehicleId());
    }

    private void notifyCustomer(Booking booking, String type, String message) {
        if (notificationManagementService != null) {
            notificationManagementService.createNotification(booking.getUser(), booking, type, message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
