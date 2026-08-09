package com.carwash.service;

import com.carwash.config.BookingPolicyProperties;
import com.carwash.domain.Booking;
import com.carwash.domain.Service;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.enums.BookingStatus;
import com.carwash.repository.BookingRepository;
import com.carwash.repository.NotificationRepository;
import com.carwash.repository.QueueEntryRepository;
import com.carwash.repository.ServiceRepository;
import com.carwash.repository.UserRepository;
import com.carwash.repository.VehicleRepository;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class BookingManagementService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceRepository serviceRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationManagementService notificationManagementService;
    private final InMemoryDataCoordinator coordinator;
    private final BookingPolicyProperties bookingPolicy;
    private final Clock clock;


    public BookingManagementService(BookingRepository bookingRepository, UserRepository userRepository,
                                    VehicleRepository vehicleRepository, ServiceRepository serviceRepository,
                                    QueueEntryRepository queueEntryRepository,
                                    NotificationRepository notificationRepository,
                                    NotificationManagementService notificationManagementService,
                                    InMemoryDataCoordinator coordinator,
                                    BookingPolicyProperties bookingPolicy,
                                    Clock clock) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "Booking repository is required");
        this.userRepository = Objects.requireNonNull(userRepository, "User repository is required");
        this.vehicleRepository = Objects.requireNonNull(vehicleRepository, "Vehicle repository is required");
        this.serviceRepository = Objects.requireNonNull(serviceRepository, "Service repository is required");
        this.queueEntryRepository = queueEntryRepository;
        this.notificationRepository = notificationRepository;
        this.notificationManagementService = notificationManagementService;
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.bookingPolicy = Objects.requireNonNull(bookingPolicy, "Booking policy is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
    }

    public Booking createBooking(String bookingId, String userId, String vehicleId, String serviceId,
                                 LocalDateTime scheduledDateTime, String specialRequest) {
        User user = new User(); user.setUserId(userId);
        Vehicle vehicle = new Vehicle(); vehicle.setVehicleId(vehicleId);
        Service service = new Service(); service.setServiceId(serviceId);
        return createBooking(new Booking(bookingId, user, vehicle, service, scheduledDateTime, specialRequest));
    }

    public Booking createBooking(Booking booking) {
        return coordinator.write(() -> {
            validateAndResolveNewBooking(booking);
            if (!bookingRepository.insert(booking)) {
                throw new BusinessRuleViolationException("Booking ID already exists");
            }
            User owner = booking.getUser();
            owner.addBooking(booking);
            if (!userRepository.update(owner)) {
                owner.removeBooking(booking.getBookingId());
                bookingRepository.deleteById(booking.getBookingId());
                throw new ResourceNotFoundException("User not found: " + owner.getUserId());
            }
            return booking;
        });
    }

    public Booking findById(String bookingId) {
        return coordinator.read(() -> requireBooking(bookingId));
    }

    public List<Booking> findAll() {
        return coordinator.read(bookingRepository::findAll);
    }

    public Booking updateBooking(String bookingId, String vehicleId, String serviceId,
                                 LocalDateTime scheduledDateTime, String specialRequest) {
        return coordinator.write(() -> {
            Booking existing = requireBooking(bookingId);
            requireModifiableBooking(existing);
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
            if (!bookingRepository.update(existing)) {
                throw new ResourceNotFoundException("Booking not found: " + bookingId);
            }
            return existing;
        });
    }

    public Booking cancelBooking(String bookingId, String customerId) {
        return coordinator.write(() -> {
            Booking booking = requireBooking(bookingId);
            validateCancellationRequest(booking, customerId);
            if (!booking.cancel()) throw new BusinessRuleViolationException("Invalid booking status transition");
            if (!bookingRepository.update(booking)) throw new ResourceNotFoundException("Booking not found: " + bookingId);
            notifyCustomer(booking, "BOOKING_CANCELLED", "Your booking has been cancelled.");
            return booking;
        });
    }

    public Booking cancelBooking(String bookingId) {
        return coordinator.write(() -> {
            Booking booking = requireBooking(bookingId);
            return cancelBooking(bookingId, requireExistingOwner(booking).getUserId());
        });
    }

    public Booking confirmBooking(String bookingId) {
        return coordinator.write(() -> {
            Booking booking = requireBooking(bookingId);
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                throw new BusinessRuleViolationException("Cancelled booking cannot be confirmed");
            }
            if (!booking.confirm()) throw new BusinessRuleViolationException("Invalid booking status transition");
            if (!bookingRepository.update(booking)) throw new ResourceNotFoundException("Booking not found: " + bookingId);
            notifyCustomer(booking, "BOOKING_CONFIRMED", "Your booking has been confirmed.");
            return booking;
        });
    }

    public void deleteBooking(String bookingId) {
        coordinator.write(() -> {
            Booking booking = requireBooking(bookingId);
            if (booking.getStatus() != BookingStatus.CANCELLED) {
                throw new BusinessRuleViolationException("Only cancelled bookings can be deleted");
            }
            boolean hasQueueEntry = booking.getQueueEntry() != null
                    || queueEntryRepository != null && queueEntryRepository.existsByBookingId(bookingId);
            if (hasQueueEntry) {
                throw new BusinessRuleViolationException("Booking cannot be deleted while a queue entry references it");
            }
            User owner = requireExistingOwner(booking);
            if (notificationRepository != null) {
                notificationRepository.findByBookingId(bookingId)
                        .forEach(notification -> owner.removeNotification(notification.getNotificationId()));
                notificationRepository.deleteByBookingId(bookingId);
            }
            owner.removeBooking(bookingId);
            if (!bookingRepository.deleteById(bookingId)) {
                throw new ResourceNotFoundException("Booking not found: " + bookingId);
            }
            if (!userRepository.update(owner)) {
                throw new ResourceNotFoundException("User not found: " + owner.getUserId());
            }
        });
    }

    private Booking requireBooking(String bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    private void validateAndResolveNewBooking(Booking booking) {
        if (booking == null) throw new BusinessRuleViolationException("Booking is required");
        if (isBlank(booking.getBookingId())) throw new BusinessRuleViolationException("Booking ID is required");
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
        validateSlotAvailability(booking.getBookingId(), booking.getScheduledDateTime(), user, vehicle);
        booking.setBookingId(booking.getBookingId().trim());
        booking.setUser(user);
        booking.setVehicle(vehicle);
        booking.setService(service);
        booking.setCreatedAt(LocalDateTime.now(clock));
    }

    private void requireModifiableBooking(Booking booking) {
        if (booking.getStatus() != BookingStatus.CREATED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException("Booking cannot be updated in its current state");
        }
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
        if (isBlank(vehicleId)) throw new BusinessRuleViolationException("Vehicle ID is required");
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
    }

    private Service resolveService(String serviceId) {
        if (isBlank(serviceId)) throw new BusinessRuleViolationException("Service ID is required");
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
    }

    private void requireVehicleOwnedBy(Vehicle vehicle, User owner, String message) {
        if (vehicle.getUserId() == null || !vehicle.getUserId().equals(owner.getUserId())) {
            throw new BusinessRuleViolationException(message);
        }
    }

    private void requireActiveService(Service service) {
        if (!service.isActive()) throw new BusinessRuleViolationException("Inactive service cannot be booked");
    }

    private void requireFutureSchedule(LocalDateTime scheduledDateTime) {
        if (scheduledDateTime == null || scheduledDateTime.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessRuleViolationException("Scheduled date/time cannot be in the past");
        }
    }

    private void validateCancellationRequest(Booking booking, String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new BusinessRuleViolationException("Customer ID is required to cancel booking");
        }
        if (booking.getUser() == null || booking.getUser().getUserId() == null
                || !booking.getUser().getUserId().equals(customerId)) {
            throw new BusinessRuleViolationException("Booking can only be cancelled by the owning customer");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (booking.getScheduledDateTime() == null || !now.isBefore(booking.getScheduledDateTime())) {
            throw new BusinessRuleViolationException("Only future bookings can be cancelled");
        }
        LocalDateTime cutoff = booking.getScheduledDateTime().minus(bookingPolicy.cancellationWindow());
        if (!now.isBefore(cutoff)) {
            throw new BusinessRuleViolationException("Booking cancellation window has closed");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BusinessRuleViolationException("Completed booking cannot be cancelled");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Cancelled booking cannot be cancelled again");
        }
    }

    private void validateSlotAvailability(String bookingId, LocalDateTime scheduledDateTime,
                                          User user, Vehicle vehicle) {
        List<Booking> bookingsInSlot = bookingRepository.findByScheduledDateTime(scheduledDateTime).stream()
                .filter(existingBooking -> !isSameBooking(existingBooking, bookingId))
                .filter(this::isActiveBooking).toList();
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

    private boolean isActiveBooking(Booking booking) {
        return booking.getStatus() != BookingStatus.CANCELLED;
    }

    private boolean isSameBooking(Booking existingBooking, String bookingId) {
        return existingBooking.getBookingId() != null && existingBooking.getBookingId().equals(bookingId);
    }

    private boolean hasSameCustomerAndVehicle(Booking existingBooking, User user, Vehicle vehicle) {
        return existingBooking.getUser() != null && existingBooking.getVehicle() != null
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
