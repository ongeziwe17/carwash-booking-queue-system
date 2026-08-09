package com.carwash.service;

import com.carwash.config.QueuePolicyProperties;
import com.carwash.domain.Booking;
import com.carwash.domain.QueueEntry;
import com.carwash.domain.Service;
import com.carwash.enums.BookingStatus;
import com.carwash.enums.QueueStatus;
import com.carwash.repository.BookingRepository;
import com.carwash.repository.QueueEntryRepository;
import com.carwash.repository.ServiceRepository;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
                rebalanceActiveQueue(queueEntryRepository.findActiveOrdered());
            } catch (RuntimeException exception) {
                queueEntryRepository.deleteById(queueEntry.getQueueEntryId());
                booking.detachQueueEntry(queueEntry.getQueueEntryId());
                bookingRepository.update(booking);
                throw exception;
            }
            return queueEntry;
        });
    }

    public QueueEntry findById(String queueEntryId) {
        return coordinator.read(() -> requireQueueEntry(queueEntryId));
    }

    public List<QueueEntry> findAll() {
        return coordinator.read(queueEntryRepository::findAllOrdered);
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
            List<QueueEntry> activeQueue = new ArrayList<>(queueEntryRepository.findActiveOrdered());
            if (position > activeQueue.size()) {
                throw new BusinessRuleViolationException("Queue position exceeds active queue size");
            }
            activeQueue.removeIf(entry -> queueEntryId.equals(entry.getQueueEntryId()));
            activeQueue.add(position - 1, queueEntry);
            rebalanceActiveQueue(activeQueue);
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
            rebalanceActiveQueue(queueEntryRepository.findActiveOrdered());
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
            rebalanceActiveQueue(queueEntryRepository.findActiveOrdered());
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

    private void rebalanceActiveQueue(List<QueueEntry> orderedActiveQueue) {
        List<Integer> estimatedWaits = new ArrayList<>(orderedActiveQueue.size());
        int estimatedWaitMin = 0;
        for (int index = 0; index < orderedActiveQueue.size(); index++) {
            estimatedWaits.add(estimatedWaitMin);
            if (index < orderedActiveQueue.size() - 1) {
                estimatedWaitMin = Math.addExact(
                        estimatedWaitMin, effectiveServiceDurationMinutes(orderedActiveQueue.get(index)));
            }
        }
        for (int index = 0; index < orderedActiveQueue.size(); index++) {
            QueueEntry queueEntry = orderedActiveQueue.get(index);
            queueEntry.updateQueueMetrics(index + 1, estimatedWaits.get(index));
            updateQueueEntry(queueEntry);
        }
    }

    private int effectiveServiceDurationMinutes(QueueEntry queueEntry) {
        Service service = queueEntry.getService();
        if (service != null && service.getEstimatedDurationMin() > 0) {
            return service.getEstimatedDurationMin();
        }
        return durationInMinutes(queuePolicy.defaultServiceDuration());
    }

    private int durationInMinutes(Duration duration) {
        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        if (seconds % 60 != 0 || duration.getNano() != 0) minutes++;
        return Math.toIntExact(Math.max(minutes, 1));
    }
}
