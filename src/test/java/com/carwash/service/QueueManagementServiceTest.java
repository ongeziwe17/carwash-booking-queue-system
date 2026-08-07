package com.carwash.service;

import com.carwash.domain.Booking;
import com.carwash.domain.QueueEntry;
import com.carwash.domain.Service;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.enums.QueueStatus;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class QueueManagementServiceTest extends ServiceTestSupport {

    @Test
    void queueEntryCreationSucceeds() {
        Booking booking = createSavedBooking();
        String queueId = ids.queueEntry();
        QueueEntry queueEntry = new QueueEntry(queueId, booking, booking.getService(), 1);
        assertEquals(queueId, queueService.createQueueEntry(queueEntry).getQueueEntryId());
    }

    @Test
    void queueEntryCreationFailsWithInvalidPosition() {
        Booking booking = createSavedBooking();
        QueueEntry queueEntry = new QueueEntry(ids.queueEntry(), booking, booking.getService(), 1);
        queueEntry.setPosition(0);
        assertThrows(BusinessRuleViolationException.class, () -> queueService.createQueueEntry(queueEntry));
    }

    @Test
    void queueEntryCompletionRequiresServiceStart() {
        QueueEntry queueEntry = createSavedQueueEntry();
        assertThrows(BusinessRuleViolationException.class,
                () -> queueService.completeQueueEntry(queueEntry.getQueueEntryId()));
        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());
        assertNotNull(queueService.completeQueueEntry(queueEntry.getQueueEntryId()).getCompletedAt());
    }

    @Test
    void createQueueEntryRejectsUnknownBooking() {
        Service service = createService();
        User user = User.withEncodedPassword(ids.user(), "Missing", "missing@example.test", "123", "hash", null);
        Vehicle vehicle = new Vehicle(ids.vehicle(), ids.plate(), "Sedan", "Toyota", "Corolla", "Blue", "");
        Booking missing = new Booking(ids.booking(), user, vehicle, service, TestDates.future(), "none");
        assertThrows(ResourceNotFoundException.class,
                () -> queueService.createQueueEntry(new QueueEntry(ids.queueEntry(), missing, service, 1)));
    }

    @Test
    void createQueueEntryRejectsUnknownService() {
        Booking booking = createSavedBooking();
        Service missing = new Service(ids.service(), "Missing", "desc", BigDecimal.TEN, 30);
        assertThrows(ResourceNotFoundException.class,
                () -> queueService.createQueueEntry(new QueueEntry(ids.queueEntry(), booking, missing, 1)));
    }

    @Test
    void createQueueEntryRejectsMismatchedBookingService() {
        Booking booking = createSavedBooking();
        Service other = createService();
        assertThrows(BusinessRuleViolationException.class,
                () -> queueService.createQueueEntry(new QueueEntry(ids.queueEntry(), booking, other, 1)));
    }

    @Test
    void queueWorkflowRejectsStartBeforeCall() {
        QueueEntry queueEntry = createSavedQueueEntry();
        assertThrows(BusinessRuleViolationException.class,
                () -> queueService.startService(queueEntry.getQueueEntryId()));
        assertEquals(QueueStatus.WAITING, queueService.findById(queueEntry.getQueueEntryId()).getQueueStatus());
    }

    @Test
    void queueWorkflowRejectsCallAlreadyCompleted() {
        QueueEntry queueEntry = completedQueueEntry();
        assertThrows(BusinessRuleViolationException.class,
                () -> queueService.callNext(queueEntry.getQueueEntryId()));
    }

    @Test
    void queueWorkflowRejectsStartAlreadyCompleted() {
        QueueEntry queueEntry = completedQueueEntry();
        assertThrows(BusinessRuleViolationException.class,
                () -> queueService.startService(queueEntry.getQueueEntryId()));
    }

    @Test
    void queueWorkflowRejectsCompleteAlreadyCompleted() {
        QueueEntry queueEntry = completedQueueEntry();
        assertThrows(BusinessRuleViolationException.class,
                () -> queueService.completeQueueEntry(queueEntry.getQueueEntryId()));
    }

    @Test
    void missingQueueLookupThrows() {
        assertThrows(ResourceNotFoundException.class, () -> queueService.findById(ids.queueEntry()));
    }

    private QueueEntry completedQueueEntry() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());
        return queueService.completeQueueEntry(queueEntry.getQueueEntryId());
    }
}
