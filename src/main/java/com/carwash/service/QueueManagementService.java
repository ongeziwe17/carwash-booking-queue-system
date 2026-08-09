package com.carwash.service;

import com.carwash.config.QueuePolicyProperties;
import com.carwash.domain.Booking;
import com.carwash.domain.QueueEntry;
import com.carwash.domain.Service;
import com.carwash.enums.QueueStatus;
import com.carwash.repository.BookingRepository;
import com.carwash.repository.QueueEntryRepository;
import com.carwash.repository.ServiceRepository;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class QueueManagementService {

    private final QueueEntryRepository queueEntryRepository;
    private final BookingRepository bookingRepository;
    private final ServiceRepository serviceRepository;
    private final NotificationManagementService notificationManagementService;
    private final InMemoryDataCoordinator coordinator;
    private final QueuePolicyProperties queuePolicy;
    private final Clock clock;


    public QueueManagementService(QueueEntryRepository queueEntryRepository,
                                  BookingRepository bookingRepository,
                                  ServiceRepository serviceRepository,
                                  NotificationManagementService notificationManagementService,
                                  InMemoryDataCoordinator coordinator,
                                  QueuePolicyProperties queuePolicy,
                                  Clock clock) {
        this.queueEntryRepository = Objects.requireNonNull(queueEntryRepository, "Queue repository is required");
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "Booking repository is required");
        this.serviceRepository = Objects.requireNonNull(serviceRepository, "Service repository is required");
        this.notificationManagementService = notificationManagementService;
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.queuePolicy = Objects.requireNonNull(queuePolicy, "Queue policy is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
    }

    public QueueEntry createQueueEntry(String queueEntryId, String bookingId, String serviceId, int position) {
        Booking booking = new Booking(); booking.setBookingId(bookingId);
        Service service = new Service(); service.setServiceId(serviceId);
        return createQueueEntry(new QueueEntry(queueEntryId, booking, service, position));
    }

    public QueueEntry createQueueEntry(QueueEntry queueEntry) {
        return coordinator.write(() -> {
            validateAndResolveQueueEntry(queueEntry);
            Booking booking = queueEntry.getBooking();
            if (booking.getQueueEntry() != null
                    && !queueEntry.getQueueEntryId().equals(booking.getQueueEntry().getQueueEntryId())) {
                throw new BusinessRuleViolationException("Booking already has a queue entry");
            }
            if (!queueEntryRepository.insert(queueEntry)) {
                throw new BusinessRuleViolationException("Queue entry ID already exists");
            }
            try {
                booking.attachQueueEntry(queueEntry);
                if (!bookingRepository.update(booking)) {
                    throw new ResourceNotFoundException("Booking not found: " + booking.getBookingId());
                }
            } catch (RuntimeException exception) {
                queueEntryRepository.deleteById(queueEntry.getQueueEntryId());
                booking.detachQueueEntry(queueEntry.getQueueEntryId());
                throw exception;
            }
            return queueEntry;
        });
    }

    public QueueEntry findById(String queueEntryId) {
        return coordinator.read(() -> requireQueueEntry(queueEntryId));
    }

    public List<QueueEntry> findAll() {
        return coordinator.read(queueEntryRepository::findAll);
    }

    public List<QueueEntry> findByServiceId(String serviceId) {
        return coordinator.read(() -> queueEntryRepository.findByServiceId(serviceId));
    }

    public QueueEntry updatePosition(String queueEntryId, int position) {
        return coordinator.write(() -> {
            if (position <= 0) throw new BusinessRuleViolationException("Queue position must be positive");
            QueueEntry queueEntry = requireQueueEntry(queueEntryId);
            if (queueEntry.getQueueStatus() != QueueStatus.WAITING) {
                throw new BusinessRuleViolationException("Only waiting queue entries can be repositioned");
            }
            queueEntry.updatePosition(position, queuePolicy.defaultServiceDuration());
            updateQueueEntry(queueEntry);
            return queueEntry;
        });
    }

    public QueueEntry callNext(String queueEntryId) {
        return coordinator.write(() -> {
            QueueEntry queueEntry = requireQueueEntry(queueEntryId);
            if (!queueEntry.callNext(LocalDateTime.now(clock))) {
                throw new BusinessRuleViolationException("Queue entry cannot be called in current state");
            }
            updateQueueEntry(queueEntry);
            notifyCustomer(queueEntry, "QUEUE_CALLED", "Your vehicle is next in the queue.");
            return queueEntry;
        });
    }

    public QueueEntry startService(String queueEntryId) {
        return coordinator.write(() -> {
            QueueEntry queueEntry = requireQueueEntry(queueEntryId);
            if (!queueEntry.startService(LocalDateTime.now(clock))) {
                throw new BusinessRuleViolationException("Queue entry cannot start service in current state");
            }
            updateQueueEntry(queueEntry);
            notifyCustomer(queueEntry, "SERVICE_STARTED", "Your service has started.");
            return queueEntry;
        });
    }

    public QueueEntry completeQueueEntry(String queueEntryId) {
        return coordinator.write(() -> {
            QueueEntry queueEntry = requireQueueEntry(queueEntryId);
            if (queueEntry.getStartedAt() == null) {
                throw new BusinessRuleViolationException("Queue entry cannot be completed before it has started");
            }
            if (!queueEntry.complete(LocalDateTime.now(clock))) {
                throw new BusinessRuleViolationException("Queue entry cannot be completed in current state");
            }
            updateQueueEntry(queueEntry);
            notifyCustomer(queueEntry, "SERVICE_COMPLETED", "Your service has been completed.");
            return queueEntry;
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

    private void notifyCustomer(QueueEntry queueEntry, String type, String message) {
        if (notificationManagementService != null && queueEntry.getBooking() != null) {
            notificationManagementService.createNotification(queueEntry.getBooking().getUser(),
                    queueEntry.getBooking(), type, message);
        }
    }

    private void validateAndResolveQueueEntry(QueueEntry queueEntry) {
        if (queueEntry == null) throw new BusinessRuleViolationException("Queue entry is required");
        if (queueEntry.getQueueEntryId() == null || queueEntry.getQueueEntryId().isBlank()) {
            throw new BusinessRuleViolationException("Queue entry ID is required");
        }
        if (queueEntry.getPosition() <= 0) throw new BusinessRuleViolationException("Queue position must be positive");
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
        Service service = serviceRepository.findById(queueEntry.getService().getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service not found: " + queueEntry.getService().getServiceId()));
        if (booking.getService() == null || booking.getService().getServiceId() == null
                || !booking.getService().getServiceId().equals(service.getServiceId())) {
            throw new BusinessRuleViolationException("Queue entry service must match booking service");
        }
        queueEntry.setQueueEntryId(queueEntry.getQueueEntryId().trim());
        queueEntry.setBooking(booking);
        queueEntry.setService(service);
        queueEntry.recalculateEstimatedWait(queuePolicy.defaultServiceDuration());
        queueEntry.setJoinedAt(LocalDateTime.now(clock));
    }
}
