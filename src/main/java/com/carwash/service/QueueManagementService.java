package com.carwash.service;

import com.carwash.domain.Booking;
import com.carwash.domain.QueueEntry;
import com.carwash.domain.Service;
import com.carwash.enums.QueueStatus;
import com.carwash.repository.BookingRepository;
import com.carwash.repository.QueueEntryRepository;
import com.carwash.repository.ServiceRepository;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.util.List;

public class QueueManagementService {

    private final QueueEntryRepository queueEntryRepository;
    private final BookingRepository bookingRepository;
    private final ServiceRepository serviceRepository;
    private final NotificationManagementService notificationManagementService;

    public QueueManagementService(QueueEntryRepository queueEntryRepository, BookingRepository bookingRepository, ServiceRepository serviceRepository) {
        this(queueEntryRepository, bookingRepository, serviceRepository, null);
    }

    public QueueManagementService(QueueEntryRepository queueEntryRepository, BookingRepository bookingRepository, ServiceRepository serviceRepository, NotificationManagementService notificationManagementService) {
        this.queueEntryRepository = queueEntryRepository;
        this.bookingRepository = bookingRepository;
        this.serviceRepository = serviceRepository;
        this.notificationManagementService = notificationManagementService;
    }

    public QueueEntry createQueueEntry(String queueEntryId, String bookingId, String serviceId, int position) {
        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        Service service = new Service();
        service.setServiceId(serviceId);
        return createQueueEntry(new QueueEntry(queueEntryId, booking, service, position));
    }

    public QueueEntry createQueueEntry(QueueEntry queueEntry) {
        validateQueueEntry(queueEntry);
        queueEntryRepository.save(queueEntry);
        return queueEntry;
    }

    public QueueEntry findById(String queueEntryId) {
        return queueEntryRepository.findById(queueEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue entry not found: " + queueEntryId));
    }

    public List<QueueEntry> findAll() { return queueEntryRepository.findAll(); }

    public List<QueueEntry> findByServiceId(String serviceId) { return queueEntryRepository.findByServiceId(serviceId); }

    public QueueEntry updatePosition(String queueEntryId, int position) {
        if (position <= 0) throw new BusinessRuleViolationException("Queue position must be positive");
        QueueEntry queueEntry = findById(queueEntryId);
        if (queueEntry.getQueueStatus() == QueueStatus.COMPLETED) throw new BusinessRuleViolationException("Completed queue entry cannot be updated");
        queueEntry.updatePosition(position);
        queueEntryRepository.save(queueEntry);
        return queueEntry;
    }

    public QueueEntry callNext(String queueEntryId) {
        QueueEntry queueEntry = findById(queueEntryId);
        if (!queueEntry.callNext()) throw new BusinessRuleViolationException("Queue entry cannot be called in current state");
        queueEntryRepository.save(queueEntry);
        notifyCustomer(queueEntry, "QUEUE_CALLED", "Your vehicle is next in the queue.");
        return queueEntry;
    }

    public QueueEntry startService(String queueEntryId) {
        QueueEntry queueEntry = findById(queueEntryId);
        if (!queueEntry.startService()) throw new BusinessRuleViolationException("Queue entry cannot start service in current state");
        queueEntryRepository.save(queueEntry);
        notifyCustomer(queueEntry, "SERVICE_STARTED", "Your service has started.");
        return queueEntry;
    }

    public QueueEntry completeQueueEntry(String queueEntryId) {
        QueueEntry queueEntry = findById(queueEntryId);
        if (queueEntry.getStartedAt() == null) throw new BusinessRuleViolationException("Queue entry cannot be completed before it has started");
        if (!queueEntry.complete()) throw new BusinessRuleViolationException("Queue entry cannot be completed in current state");
        queueEntryRepository.save(queueEntry);
        notifyCustomer(queueEntry, "SERVICE_COMPLETED", "Your service has been completed.");
        return queueEntry;
    }

    public void deleteQueueEntry(String queueEntryId) {
        findById(queueEntryId);
        queueEntryRepository.delete(queueEntryId);
    }

    private void notifyCustomer(QueueEntry queueEntry, String type, String message) {
        if (notificationManagementService != null && queueEntry.getBooking() != null) {
            notificationManagementService.createNotification(queueEntry.getBooking().getUser(), queueEntry.getBooking(), type, message);
        }
    }

    private void validateQueueEntry(QueueEntry queueEntry) {
        if (queueEntry == null) throw new BusinessRuleViolationException("Queue entry is required");
        if (queueEntry.getPosition() <= 0) throw new BusinessRuleViolationException("Queue position must be positive");
        Booking booking = bookingRepository.findById(queueEntry.getBooking().getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + queueEntry.getBooking().getBookingId()));
        Service service = serviceRepository.findById(queueEntry.getService().getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + queueEntry.getService().getServiceId()));
        if (booking.getService() == null || booking.getService().getServiceId() == null || !booking.getService().getServiceId().equals(service.getServiceId())) {
            throw new BusinessRuleViolationException("Queue entry service must match booking service");
        }
        queueEntry.setBooking(booking);
        queueEntry.setService(service);
    }
}
