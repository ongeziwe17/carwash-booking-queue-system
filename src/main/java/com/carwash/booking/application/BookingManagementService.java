package com.carwash.booking.application;

import com.carwash.notification.application.NotificationManagementService;
import com.carwash.queue.application.LifecycleStateSnapshot;
import com.carwash.queue.application.QueueOrderingService;

import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.booking.domain.Booking;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.catalog.domain.ServiceRepository;
import com.carwash.identity.domain.UserRepository;
import com.carwash.vehicle.domain.VehicleRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class BookingManagementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingManagementService.class);

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceRepository serviceRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationManagementService notificationManagementService;
    private final QueueOrderingService queueOrdering;
    private final InMemoryDataCoordinator coordinator;
    private final BookingPolicyProperties bookingPolicy;
    private final BookingSlotPolicyService slotPolicy;
    private final Clock clock;


    public BookingManagementService(BookingRepository bookingRepository, UserRepository userRepository,
                                    VehicleRepository vehicleRepository, ServiceRepository serviceRepository,
                                    QueueEntryRepository queueEntryRepository,
                                    NotificationRepository notificationRepository,
                                    NotificationManagementService notificationManagementService,
                                    QueueOrderingService queueOrdering,
                                    InMemoryDataCoordinator coordinator,
                                    BookingPolicyProperties bookingPolicy,
                                    Clock clock) {
        this(bookingRepository, userRepository, vehicleRepository, serviceRepository,
                queueEntryRepository, notificationRepository, notificationManagementService,
                queueOrdering, coordinator, bookingPolicy,
                new BookingSlotPolicyService(bookingRepository, bookingPolicy, clock), clock);
    }

    public BookingManagementService(BookingRepository bookingRepository, UserRepository userRepository,
                                    VehicleRepository vehicleRepository, ServiceRepository serviceRepository,
                                    QueueEntryRepository queueEntryRepository,
                                    NotificationRepository notificationRepository,
                                    NotificationManagementService notificationManagementService,
                                    QueueOrderingService queueOrdering,
                                    InMemoryDataCoordinator coordinator,
                                    BookingPolicyProperties bookingPolicy,
                                    BookingSlotPolicyService slotPolicy,
                                    Clock clock) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "Booking repository is required");
        this.userRepository = Objects.requireNonNull(userRepository, "User repository is required");
        this.vehicleRepository = Objects.requireNonNull(vehicleRepository, "Vehicle repository is required");
        this.serviceRepository = Objects.requireNonNull(serviceRepository, "Service repository is required");
        this.queueEntryRepository = Objects.requireNonNull(queueEntryRepository, "Queue repository is required");
        this.notificationRepository = notificationRepository;
        this.notificationManagementService = notificationManagementService;
        this.queueOrdering = Objects.requireNonNull(queueOrdering, "Queue ordering service is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.bookingPolicy = Objects.requireNonNull(bookingPolicy, "Booking policy is required");
        this.slotPolicy = Objects.requireNonNull(slotPolicy, "Booking slot policy is required");
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

    public Booking updateBooking(String bookingId, String vehicleId, String serviceId, String specialRequest) {
        return coordinator.write(() -> {
            Booking existing = requireBooking(bookingId);
            requireModifiableBooking(existing);
            if (queueEntryRepository.existsActiveByBookingId(bookingId)) {
                throw new BusinessRuleViolationException("Booking cannot be updated while it has an active queue entry");
            }
            User owner = requireExistingOwner(existing);
            Vehicle vehicle = resolveVehicle(vehicleId);
            requireVehicleOwnedBy(vehicle, owner, "Vehicle does not belong to booking owner");
            Service service = resolveService(serviceId);
            requireActiveService(service);
            slotPolicy.validateBookableSlot(
                    bookingId, existing.getScheduledDateTime(), service, owner, vehicle);

            Vehicle originalVehicle = existing.getVehicle();
            Service originalService = existing.getService();
            String originalSpecialRequest = existing.getSpecialRequest();
            try {
                existing.setVehicle(vehicle);
                existing.setService(service);
                existing.setSpecialRequest(specialRequest);
                if (!bookingRepository.update(existing)) {
                    throw new ResourceNotFoundException("Booking not found: " + bookingId);
                }
            } catch (RuntimeException exception) {
                existing.setVehicle(originalVehicle);
                existing.setService(originalService);
                existing.setSpecialRequest(originalSpecialRequest);
                throw exception;
            }
            return existing;
        });
    }

    public Booking rescheduleBooking(String bookingId, LocalDateTime scheduledDateTime) {
        return coordinator.write(() -> {
            Booking booking = requireBooking(bookingId);
            requireReschedulableBooking(booking);
            LocalDateTime now = LocalDateTime.now(clock);
            requireCurrentFutureSchedule(booking, now);
            requireOpenReschedulingWindow(booking, now);
            if (queueEntryRepository.existsActiveByBookingId(bookingId)) {
                throw new BusinessRuleViolationException(
                        "Booking cannot be rescheduled while it has an active queue entry");
            }
            User owner = requireExistingOwner(booking);
            Vehicle vehicle = resolveCurrentVehicle(booking);
            requireVehicleOwnedBy(vehicle, owner, "Vehicle does not belong to booking owner");
            Service service = resolveCurrentService(booking);
            requireActiveService(service);
            slotPolicy.validateBookableSlot(bookingId, scheduledDateTime, service, owner, vehicle);

            LocalDateTime originalScheduledDateTime = booking.getScheduledDateTime();
            try {
                booking.setScheduledDateTime(scheduledDateTime);
                if (!bookingRepository.update(booking)) {
                    throw new ResourceNotFoundException("Booking not found: " + bookingId);
                }
            } catch (RuntimeException exception) {
                booking.setScheduledDateTime(originalScheduledDateTime);
                throw exception;
            }

            notifyCustomerBestEffort(
                    booking,
                    "BOOKING_RESCHEDULED",
                    "Your booking has been rescheduled to " + scheduledDateTime + "."
            );
            return booking;
        });
    }

    public Booking cancelBooking(String bookingId, String customerId) {
        return coordinator.write(() -> {
            Booking booking = requireBooking(bookingId);
            validateCancellationRequest(booking, customerId);
            QueueEntry activeQueueEntry = findActiveQueueEntry(bookingId);
            if (activeQueueEntry != null && activeQueueEntry.getQueueStatus() == QueueStatus.IN_PROGRESS) {
                throw new BusinessRuleViolationException("Booking cannot be cancelled while service is in progress");
            }
            if (activeQueueEntry != null
                    && activeQueueEntry.getQueueStatus() != QueueStatus.WAITING
                    && activeQueueEntry.getQueueStatus() != QueueStatus.CALLED) {
                throw new BusinessRuleViolationException("Booking cannot be cancelled with its current queue state");
            }
            LifecycleStateSnapshot.BookingState bookingState = LifecycleStateSnapshot.booking(booking);
            List<LifecycleStateSnapshot.QueueEntryState> queueStates = activeQueueEntry == null
                    ? List.of()
                    : LifecycleStateSnapshot.queueEntries(queueEntryRepository.findActiveOrdered());
            boolean queueRemoved = false;
            try {
                if (activeQueueEntry != null) {
                    if (!queueEntryRepository.deleteById(activeQueueEntry.getQueueEntryId())) {
                        throw new ResourceNotFoundException(
                                "Queue entry not found: " + activeQueueEntry.getQueueEntryId());
                    }
                    queueRemoved = true;
                    if (booking.getQueueEntry() != null) {
                        booking.detachQueueEntry(booking.getQueueEntry().getQueueEntryId());
                    }
                }
                if (!booking.cancel()) {
                    throw new BusinessRuleViolationException("Invalid booking status transition");
                }
                if (!bookingRepository.update(booking)) {
                    throw new ResourceNotFoundException("Booking not found: " + bookingId);
                }
                if (queueRemoved) queueOrdering.rebalanceActiveQueue();
            } catch (RuntimeException exception) {
                rollbackCancellation(exception, bookingState, queueStates);
                throw exception;
            }
            notifyCustomerBestEffort(booking, "BOOKING_CANCELLED", "Your booking has been cancelled.");
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
        if (bookingRepository.existsById(booking.getBookingId().trim())) {
            throw new BusinessRuleViolationException("Booking ID already exists");
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
        slotPolicy.validateBookableSlot(null, booking.getScheduledDateTime(), service, user, vehicle);
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

    private void requireReschedulableBooking(Booking booking) {
        if (booking.getStatus() != BookingStatus.CREATED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException("Booking cannot be rescheduled in its current state");
        }
    }

    private void requireCurrentFutureSchedule(Booking booking, LocalDateTime now) {
        if (booking.getScheduledDateTime() == null || !booking.getScheduledDateTime().isAfter(now)) {
            throw new BusinessRuleViolationException("Only future bookings can be rescheduled");
        }
    }

    private void requireOpenReschedulingWindow(Booking booking, LocalDateTime now) {
        LocalDateTime cutoff = booking.getScheduledDateTime().minus(bookingPolicy.cancellationWindow());
        if (!now.isBefore(cutoff)) {
            throw new BusinessRuleViolationException("Booking rescheduling window has closed");
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

    private Vehicle resolveCurrentVehicle(Booking booking) {
        if (booking.getVehicle() == null || isBlank(booking.getVehicle().getVehicleId())) {
            throw new BusinessRuleViolationException("Booking vehicle is required");
        }
        return resolveVehicle(booking.getVehicle().getVehicleId());
    }

    private Service resolveService(String serviceId) {
        if (isBlank(serviceId)) throw new BusinessRuleViolationException("Service ID is required");
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
    }

    private Service resolveCurrentService(Booking booking) {
        if (booking.getService() == null || isBlank(booking.getService().getServiceId())) {
            throw new BusinessRuleViolationException("Booking service is required");
        }
        return resolveService(booking.getService().getServiceId());
    }

    private void requireVehicleOwnedBy(Vehicle vehicle, User owner, String message) {
        if (vehicle.getUserId() == null || !vehicle.getUserId().equals(owner.getUserId())) {
            throw new BusinessRuleViolationException(message);
        }
    }

    private void requireActiveService(Service service) {
        if (!service.isActive()) throw new BusinessRuleViolationException("Inactive service cannot be booked");
    }

    private void validateCancellationRequest(Booking booking, String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new BusinessRuleViolationException("Customer ID is required to cancel booking");
        }
        if (booking.getUser() == null || booking.getUser().getUserId() == null
                || !booking.getUser().getUserId().equals(customerId)) {
            throw new BusinessRuleViolationException("Booking can only be cancelled by the owning customer");
        }
        if (booking.getStatus() == BookingStatus.IN_SERVICE) {
            throw new BusinessRuleViolationException("Booking cannot be cancelled while service is in progress");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BusinessRuleViolationException("Completed booking cannot be cancelled");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Cancelled booking cannot be cancelled again");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (booking.getScheduledDateTime() == null || !now.isBefore(booking.getScheduledDateTime())) {
            throw new BusinessRuleViolationException("Only future bookings can be cancelled");
        }
        LocalDateTime cutoff = booking.getScheduledDateTime().minus(bookingPolicy.cancellationWindow());
        if (!now.isBefore(cutoff)) {
            throw new BusinessRuleViolationException("Booking cancellation window has closed");
        }
    }

    private QueueEntry findActiveQueueEntry(String bookingId) {
        return queueEntryRepository.findByBookingId(bookingId).stream()
                .filter(queueEntry -> queueEntry.getQueueStatus() != null)
                .filter(queueEntry -> queueEntry.getQueueStatus().isActive())
                .findFirst()
                .orElse(null);
    }

    private void rollbackCancellation(RuntimeException failure,
                                      LifecycleStateSnapshot.BookingState bookingState,
                                      List<LifecycleStateSnapshot.QueueEntryState> queueStates) {
        try {
            bookingState.restore();
            for (LifecycleStateSnapshot.QueueEntryState queueState : queueStates) {
                queueState.restore();
                QueueEntry queueEntry = queueState.queueEntry();
                if (queueEntryRepository.existsById(queueEntry.getQueueEntryId())) {
                    if (!queueEntryRepository.update(queueEntry)) {
                        throw new ResourceNotFoundException("Queue entry not found: " + queueEntry.getQueueEntryId());
                    }
                } else if (!queueEntryRepository.insert(queueEntry)) {
                    throw new BusinessRuleViolationException("Queue entry ID already exists");
                }
            }
            if (!bookingRepository.update(bookingState.booking())) {
                throw new ResourceNotFoundException("Booking not found: " + bookingState.booking().getBookingId());
            }
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private void notifyCustomer(Booking booking, String type, String message) {
        if (notificationManagementService != null) {
            notificationManagementService.createNotification(booking.getUser(), booking, type, message);
        }
    }

    private void notifyCustomerBestEffort(Booking booking, String type, String message) {
        try {
            notifyCustomer(booking, type, message);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to create {} notification for booking {}",
                    type, booking.getBookingId(), exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
