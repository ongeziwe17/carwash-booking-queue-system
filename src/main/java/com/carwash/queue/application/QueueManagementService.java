package com.carwash.queue.application;

import com.carwash.notification.application.NotificationManagementService;

import com.carwash.booking.domain.Booking;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.identity.domain.Role;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.catalog.domain.ServiceRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class QueueManagementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueManagementService.class);

    private final QueueEntryRepository queueEntryRepository;
    private final BookingRepository bookingRepository;
    private final ServiceRepository serviceRepository;
    private final NotificationManagementService notificationManagementService;
    private final InMemoryDataCoordinator coordinator;
    private final QueueOrderingService queueOrdering;
    private final Clock clock;


    public QueueManagementService(QueueEntryRepository queueEntryRepository,
                                  BookingRepository bookingRepository,
                                  ServiceRepository serviceRepository,
                                  NotificationManagementService notificationManagementService,
                                  InMemoryDataCoordinator coordinator,
                                  QueueOrderingService queueOrdering,
                                  Clock clock) {
        this.queueEntryRepository = Objects.requireNonNull(queueEntryRepository, "Queue repository is required");
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "Booking repository is required");
        this.serviceRepository = Objects.requireNonNull(serviceRepository, "Service repository is required");
        this.notificationManagementService = notificationManagementService;
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.queueOrdering = Objects.requireNonNull(queueOrdering, "Queue ordering service is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
    }

    public QueueEntry createQueueEntry(String queueEntryId, String bookingId, String serviceId) {
        Booking booking = new Booking(); booking.setBookingId(bookingId);
        Service service = new Service(); service.setServiceId(serviceId);
        return createQueueEntry(new QueueEntry(queueEntryId, booking, service));
    }

    public QueueEntry createQueueEntry(QueueEntry queueEntry) {
        return coordinator.write(() -> {
            validateAndResolveQueueEntry(queueEntry);
            Booking booking = queueEntry.getBooking();
            if (booking.getQueueEntry() != null
                    && !queueEntry.getQueueEntryId().equals(booking.getQueueEntry().getQueueEntryId())) {
                throw new BusinessRuleViolationException("Booking already has a queue entry");
            }
            List<QueueEntry> activeQueue = queueEntryRepository.findActiveOrdered();
            initializeNewQueueEntry(queueEntry, activeQueue.size() + 1);
            if (!queueEntryRepository.insert(queueEntry)) {
                throw new BusinessRuleViolationException("Queue entry ID already exists");
            }
            try {
                booking.attachQueueEntry(queueEntry);
                if (!bookingRepository.update(booking)) {
                    throw new ResourceNotFoundException("Booking not found: " + booking.getBookingId());
                }
                queueOrdering.rebalanceActiveQueue();
            } catch (RuntimeException exception) {
                queueEntryRepository.deleteById(queueEntry.getQueueEntryId());
                booking.detachQueueEntry(queueEntry.getQueueEntryId());
                bookingRepository.update(booking);
                throw exception;
            }
            return snapshotQueueEntry(queueEntry);
        });
    }

    public QueueEntry findById(String queueEntryId) {
        return coordinator.read(() -> snapshotQueueEntry(requireQueueEntry(queueEntryId)));
    }

    public List<QueueEntry> findAll() {
        return coordinator.read(() -> queueEntryRepository.findAllOrdered().stream()
                .map(this::snapshotQueueEntry)
                .toList());
    }

    public List<QueueEntry> findByServiceId(String serviceId) {
        return coordinator.read(() -> queueEntryRepository.findByServiceId(serviceId).stream()
                .map(this::snapshotQueueEntry)
                .toList());
    }

    public QueueEntry updatePosition(String queueEntryId, int position) {
        return coordinator.write(() -> {
            if (position <= 0) throw new BusinessRuleViolationException("Queue position must be positive");
            QueueEntry queueEntry = requireQueueEntry(queueEntryId);
            if (queueEntry.getQueueStatus() != QueueStatus.WAITING) {
                throw new BusinessRuleViolationException("Only waiting queue entries can be repositioned");
            }
            List<QueueEntry> activeQueue = new ArrayList<>(queueEntryRepository.findActiveOrdered());
            if (position > activeQueue.size()) {
                throw new BusinessRuleViolationException("Queue position exceeds active queue size");
            }
            activeQueue.removeIf(entry -> queueEntryId.equals(entry.getQueueEntryId()));
            activeQueue.add(position - 1, queueEntry);
            queueOrdering.rebalanceActiveQueue(activeQueue);
            return snapshotQueueEntry(queueEntry);
        });
    }

    public QueueEntry callNext() {
        return coordinator.write(() -> callWaitingEntry(queueEntryRepository.findNextWaiting()
                .orElseThrow(() -> new ResourceNotFoundException("No waiting queue entry available"))));
    }

    public QueueEntry callQueueEntry(String queueEntryId) {
        return coordinator.write(() -> callWaitingEntry(requireQueueEntry(queueEntryId)));
    }

    public QueueEntry startService(String queueEntryId) {
        return coordinator.write(() -> {
            QueueEntry queueEntry = requireQueueEntry(queueEntryId);
            if (queueEntry.getQueueStatus() != QueueStatus.CALLED) {
                throw new BusinessRuleViolationException("Queue entry cannot start service in current state");
            }
            Booking booking = requireCanonicalBooking(queueEntry);
            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                throw new BusinessRuleViolationException("Booking must be confirmed before service can start");
            }
            LifecycleStateSnapshot.QueueEntryState queueState = LifecycleStateSnapshot.queueEntry(queueEntry);
            LifecycleStateSnapshot.BookingState bookingState = LifecycleStateSnapshot.booking(booking);
            try {
                queueEntry.setBooking(booking);
                if (!queueEntry.startService(LocalDateTime.now(clock))) {
                    throw new BusinessRuleViolationException("Queue entry cannot start service in current state");
                }
                if (!booking.startService()) {
                    throw new BusinessRuleViolationException("Booking must be confirmed before service can start");
                }
                updateQueueEntry(queueEntry);
                updateBooking(booking);
            } catch (RuntimeException exception) {
                rollbackLifecycle(exception, bookingState, List.of(queueState));
                throw exception;
            }
            notifyCustomerBestEffort(queueEntry, "SERVICE_STARTED", "Your service has started.");
            return snapshotQueueEntry(queueEntry);
        });
    }

    public QueueEntry completeQueueEntry(String queueEntryId) {
        return coordinator.write(() -> {
            QueueEntry queueEntry = requireQueueEntry(queueEntryId);
            if (queueEntry.getStartedAt() == null) {
                throw new BusinessRuleViolationException("Queue entry cannot be completed before it has started");
            }
            if (queueEntry.getQueueStatus() != QueueStatus.IN_PROGRESS) {
                throw new BusinessRuleViolationException("Queue entry cannot be completed in current state");
            }
            Booking booking = requireCanonicalBooking(queueEntry);
            if (booking.getStatus() != BookingStatus.IN_SERVICE) {
                throw new BusinessRuleViolationException("Booking must be in service before queue completion");
            }
            List<LifecycleStateSnapshot.QueueEntryState> queueStates =
                    LifecycleStateSnapshot.queueEntries(queueEntryRepository.findActiveOrdered());
            LifecycleStateSnapshot.BookingState bookingState = LifecycleStateSnapshot.booking(booking);
            try {
                queueEntry.setBooking(booking);
                if (!queueEntry.complete(LocalDateTime.now(clock))) {
                    throw new BusinessRuleViolationException("Queue entry cannot be completed in current state");
                }
                if (!booking.completeService()) {
                    throw new BusinessRuleViolationException("Booking must be in service before queue completion");
                }
                updateQueueEntry(queueEntry);
                updateBooking(booking);
                queueOrdering.rebalanceActiveQueue();
            } catch (RuntimeException exception) {
                rollbackLifecycle(exception, bookingState, queueStates);
                throw exception;
            }
            notifyCustomerBestEffort(queueEntry, "SERVICE_COMPLETED", "Your service has been completed.");
            return snapshotQueueEntry(queueEntry);
        });
    }

    public void deleteQueueEntry(String queueEntryId) {
        coordinator.write(() -> {
            QueueEntry queueEntry = requireQueueEntry(queueEntryId);
            if (queueEntry.getQueueStatus() != QueueStatus.WAITING) {
                throw new BusinessRuleViolationException("Only waiting queue entries can be deleted");
            }
            Booking booking = queueEntry.getBooking();
            if (!queueEntryRepository.deleteById(queueEntryId)) {
                throw new ResourceNotFoundException("Queue entry not found: " + queueEntryId);
            }
            if (booking != null) {
                booking.detachQueueEntry(queueEntryId);
                if (!bookingRepository.update(booking)) {
                    throw new ResourceNotFoundException("Booking not found: " + booking.getBookingId());
                }
            }
            queueOrdering.rebalanceActiveQueue();
        });
    }

    private QueueEntry requireQueueEntry(String queueEntryId) {
        return queueEntryRepository.findById(queueEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue entry not found: " + queueEntryId));
    }

    private void updateQueueEntry(QueueEntry queueEntry) {
        if (!queueEntryRepository.update(queueEntry)) {
            throw new ResourceNotFoundException("Queue entry not found: " + queueEntry.getQueueEntryId());
        }
    }

    private void updateBooking(Booking booking) {
        if (!bookingRepository.update(booking)) {
            throw new ResourceNotFoundException("Booking not found: " + booking.getBookingId());
        }
    }

    private QueueEntry callWaitingEntry(QueueEntry queueEntry) {
        if (queueEntry.getQueueStatus() != QueueStatus.WAITING) {
            throw new BusinessRuleViolationException("Queue entry cannot be called in current state");
        }
        LifecycleStateSnapshot.QueueEntryState queueState = LifecycleStateSnapshot.queueEntry(queueEntry);
        try {
            if (!queueEntry.callNext(LocalDateTime.now(clock))) {
                throw new BusinessRuleViolationException("Queue entry cannot be called in current state");
            }
            updateQueueEntry(queueEntry);
            notifyCustomer(queueEntry, "QUEUE_CALLED", "Your vehicle is next in the queue.");
            return snapshotQueueEntry(queueEntry);
        } catch (RuntimeException exception) {
            rollbackQueueCall(exception, queueState);
            throw exception;
        }
    }

    private void rollbackQueueCall(RuntimeException failure,
                                   LifecycleStateSnapshot.QueueEntryState queueState) {
        try {
            queueState.restore();
            updateQueueEntry(queueState.queueEntry());
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private Booking requireCanonicalBooking(QueueEntry queueEntry) {
        Booking associatedBooking = queueEntry.getBooking();
        if (associatedBooking == null || associatedBooking.getBookingId() == null
                || associatedBooking.getBookingId().isBlank()) {
            throw new BusinessRuleViolationException("Queue entry must have an associated booking");
        }
        String bookingId = associatedBooking.getBookingId();
        Booking canonicalBooking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (!bookingId.equals(canonicalBooking.getBookingId())) {
            throw new BusinessRuleViolationException("Queue entry booking association is inconsistent");
        }
        if (canonicalBooking.getQueueEntry() == null
                || !queueEntry.getQueueEntryId().equals(canonicalBooking.getQueueEntry().getQueueEntryId())) {
            throw new BusinessRuleViolationException("Queue entry booking association is inconsistent");
        }
        return canonicalBooking;
    }

    private void rollbackLifecycle(RuntimeException failure,
                                   LifecycleStateSnapshot.BookingState bookingState,
                                   List<LifecycleStateSnapshot.QueueEntryState> queueStates) {
        try {
            bookingState.restore();
            for (LifecycleStateSnapshot.QueueEntryState queueState : queueStates) {
                queueState.restore();
                updateQueueEntry(queueState.queueEntry());
            }
            updateBooking(bookingState.booking());
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private void notifyCustomer(QueueEntry queueEntry, String type, String message) {
        if (notificationManagementService != null && queueEntry.getBooking() != null) {
            notificationManagementService.createNotification(queueEntry.getBooking().getUser(),
                    queueEntry.getBooking(), type, message);
        }
    }

    private void notifyCustomerBestEffort(QueueEntry queueEntry, String type, String message) {
        try {
            notifyCustomer(queueEntry, type, message);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to create {} notification for queue entry {}",
                    type, queueEntry.getQueueEntryId(), exception);
        }
    }

    private void validateAndResolveQueueEntry(QueueEntry queueEntry) {
        if (queueEntry == null) throw new BusinessRuleViolationException("Queue entry is required");
        if (queueEntry.getQueueEntryId() == null || queueEntry.getQueueEntryId().isBlank()) {
            throw new BusinessRuleViolationException("Queue entry ID is required");
        }
        if (queueEntry.getBooking() == null || queueEntry.getBooking().getBookingId() == null
                || queueEntry.getBooking().getBookingId().isBlank()) {
            throw new BusinessRuleViolationException("Booking is required");
        }
        if (queueEntry.getService() == null || queueEntry.getService().getServiceId() == null
                || queueEntry.getService().getServiceId().isBlank()) {
            throw new BusinessRuleViolationException("Service is required");
        }
        Booking booking = bookingRepository.findById(queueEntry.getBooking().getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found: " + queueEntry.getBooking().getBookingId()));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException("Only confirmed bookings can join the queue");
        }
        Service service = serviceRepository.findById(queueEntry.getService().getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service not found: " + queueEntry.getService().getServiceId()));
        if (booking.getService() == null || booking.getService().getServiceId() == null
                || !booking.getService().getServiceId().equals(service.getServiceId())) {
            throw new BusinessRuleViolationException("Queue entry service must match booking service");
        }
        if (!service.isActive()) {
            throw new BusinessRuleViolationException("Inactive service cannot join the queue");
        }
        if (queueEntryRepository.existsActiveByBookingId(booking.getBookingId())) {
            throw new BusinessRuleViolationException("Booking already has an active queue entry");
        }
        queueEntry.setQueueEntryId(queueEntry.getQueueEntryId().trim());
        queueEntry.setBooking(booking);
        queueEntry.setService(service);
    }

    private void initializeNewQueueEntry(QueueEntry queueEntry, int position) {
        queueEntry.setQueueStatus(QueueStatus.WAITING);
        queueEntry.setJoinedAt(LocalDateTime.now(clock));
        queueEntry.setCalledAt(null);
        queueEntry.setStartedAt(null);
        queueEntry.setCompletedAt(null);
        queueEntry.updateQueueMetrics(position, 0);
    }

    private QueueEntry snapshotQueueEntry(QueueEntry source) {
        QueueEntry snapshot = new QueueEntry();
        snapshot.setQueueEntryId(source.getQueueEntryId());
        snapshot.setBooking(snapshotBooking(source.getBooking()));
        snapshot.setService(snapshotService(source.getService()));
        snapshot.setPosition(source.getPosition());
        snapshot.setQueueStatus(source.getQueueStatus());
        snapshot.setJoinedAt(source.getJoinedAt());
        snapshot.setCalledAt(source.getCalledAt());
        snapshot.setStartedAt(source.getStartedAt());
        snapshot.setCompletedAt(source.getCompletedAt());
        snapshot.setEstimatedWaitMin(source.getEstimatedWaitMin());
        return snapshot;
    }

    private Booking snapshotBooking(Booking source) {
        if (source == null) return null;
        Booking snapshot = new Booking();
        snapshot.setBookingId(source.getBookingId());
        snapshot.setUser(snapshotUser(source.getUser()));
        snapshot.setVehicle(snapshotVehicle(source.getVehicle()));
        snapshot.setService(snapshotService(source.getService()));
        snapshot.setScheduledDateTime(source.getScheduledDateTime());
        snapshot.setStatus(source.getStatus());
        snapshot.setCreatedAt(source.getCreatedAt());
        snapshot.setSpecialRequest(source.getSpecialRequest());
        return snapshot;
    }

    private User snapshotUser(User source) {
        if (source == null) return null;
        User snapshot = new User();
        snapshot.setUserId(source.getUserId());
        snapshot.setFullName(source.getFullName());
        snapshot.setEmail(source.getEmail());
        snapshot.setPhone(source.getPhone());
        snapshot.setAccountStatus(source.getAccountStatus());
        snapshot.setCreatedAt(source.getCreatedAt());
        snapshot.setLastLoginAt(source.getLastLoginAt());
        snapshot.setRole(snapshotRole(source.getRole()));
        return snapshot;
    }

    private Role snapshotRole(Role source) {
        if (source == null) return null;
        return new Role(source.getRoleId(), source.getRoleName(), source.getDescription(), source.getPermissions());
    }

    private Vehicle snapshotVehicle(Vehicle source) {
        if (source == null) return null;
        Vehicle snapshot = new Vehicle(
                source.getVehicleId(), source.getPlateNumber(), source.getVehicleType(), source.getBrand(),
                source.getModel(), source.getColor(), source.getNotes());
        snapshot.setUserId(source.getUserId());
        return snapshot;
    }

    private Service snapshotService(Service source) {
        if (source == null) return null;
        Service snapshot = new Service();
        snapshot.setServiceId(source.getServiceId());
        snapshot.setServiceName(source.getServiceName());
        snapshot.setDescription(source.getDescription());
        snapshot.setPrice(source.getPrice());
        snapshot.setEstimatedDurationMin(source.getEstimatedDurationMin());
        snapshot.setActive(source.isActive());
        snapshot.setCreatedAt(source.getCreatedAt());
        return snapshot;
    }
}
