package com.carwash.service;

import com.carwash.domain.Booking;
import com.carwash.domain.QueueEntry;
import com.carwash.domain.Service;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.enums.BookingStatus;
import com.carwash.enums.QueueStatus;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class QueueManagementServiceTest extends ServiceTestSupport {

    @Test
    void queueEntryCreationSucceeds() {
        Booking booking = createConfirmedBooking();
        String queueId = ids.queueEntry();
        QueueEntry queueEntry = new QueueEntry(queueId, booking, booking.getService(), 1);
        QueueEntry created = queueService.createQueueEntry(queueEntry);

        assertEquals(queueId, created.getQueueEntryId());
        assertEquals(QueueStatus.WAITING, created.getQueueStatus());
        assertSame(booking, created.getBooking());
        assertSame(booking.getService(), created.getService());
        assertSame(created, booking.getQueueEntry());
    }

    @Test
    void queueEntryCreationRecalculatesWaitAfterResolvingCanonicalService() {
        Booking booking = createConfirmedBooking();

        QueueEntry queueEntry = queueService.createQueueEntry(
                booking.getBookingId() + "-queue", booking.getBookingId(), booking.getService().getServiceId(), 2);

        assertEquals(booking.getService().getEstimatedDurationMin(), queueEntry.getEstimatedWaitMin());
    }

    @Test
    void configuredFallbackDurationIsUsedWhenServiceDurationIsUnavailable() {
        QueueEntry queueEntry = new QueueEntry();
        queueEntry.setPosition(3);

        queueEntry.recalculateEstimatedWait(Duration.ofMinutes(12));

        assertEquals(24, queueEntry.getEstimatedWaitMin());
    }

    @Test
    void queueLifecycleTimestampsUseSuppliedClock() {
        Booking booking = createConfirmedBooking();
        QueueEntry queueEntry = queueService.createQueueEntry(
                new QueueEntry(ids.queueEntry(), booking, booking.getService(), 1));
        LocalDateTime expected = LocalDateTime.now(clock);

        assertEquals(expected, queueEntry.getJoinedAt());
        assertEquals(expected, queueService.callNext(queueEntry.getQueueEntryId()).getCalledAt());
        assertEquals(expected, queueService.startService(queueEntry.getQueueEntryId()).getStartedAt());
        assertEquals(expected, queueService.completeQueueEntry(queueEntry.getQueueEntryId()).getCompletedAt());
    }

    @Test
    void queueEntryCreationFailsWithInvalidPosition() {
        Booking booking = createConfirmedBooking();
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
        Booking booking = createConfirmedBooking();
        Service missing = new Service(ids.service(), "Missing", "desc", BigDecimal.TEN, 30);
        assertThrows(ResourceNotFoundException.class,
                () -> queueService.createQueueEntry(new QueueEntry(ids.queueEntry(), booking, missing, 1)));
    }

    @Test
    void createQueueEntryRejectsMismatchedBookingService() {
        Booking booking = createConfirmedBooking();
        Service other = createService();
        assertThrows(BusinessRuleViolationException.class,
                () -> queueService.createQueueEntry(new QueueEntry(ids.queueEntry(), booking, other, 1)));
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CREATED", "CANCELLED", "IN_SERVICE", "COMPLETED"})
    void createQueueEntryRejectsNonConfirmedBookings(BookingStatus status) {
        Booking booking = createSavedBooking(TestDates.futureDays(2), status);

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> queueService.createQueueEntry(
                        new QueueEntry(ids.queueEntry(), booking, booking.getService(), 1)));

        assertEquals("Only confirmed bookings can join the queue", exception.getMessage());
        assertTrue(queueRepository.findAll().isEmpty());
        assertNull(booking.getQueueEntry());
    }

    @Test
    void createQueueEntryRejectsInactiveService() {
        Booking booking = createConfirmedBooking();
        catalogService.deactivateService(booking.getService().getServiceId());

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> queueService.createQueueEntry(
                        new QueueEntry(ids.queueEntry(), booking, booking.getService(), 1)));

        assertEquals("Inactive service cannot join the queue", exception.getMessage());
        assertTrue(queueRepository.findAll().isEmpty());
        assertNull(booking.getQueueEntry());
    }

    @ParameterizedTest
    @EnumSource(value = QueueStatus.class, names = {"WAITING", "CALLED", "IN_PROGRESS"})
    void createQueueEntryRejectsSecondActiveEntry(QueueStatus activeStatus) {
        Booking booking = createConfirmedBooking();
        QueueEntry existing = queueService.createQueueEntry(
                new QueueEntry(ids.queueEntry(), booking, booking.getService(), 1));
        existing.setQueueStatus(activeStatus);
        assertTrue(queueRepository.update(existing));

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> queueService.createQueueEntry(
                        new QueueEntry(ids.queueEntry(), booking, booking.getService(), 2)));

        assertEquals("Booking already has an active queue entry", exception.getMessage());
        assertEquals(1, queueRepository.findByBookingId(booking.getBookingId()).size());
        assertSame(existing, booking.getQueueEntry());
    }

    @Test
    void queueEntryCreationNormalizesServerControlledState() {
        Booking booking = createConfirmedBooking();
        QueueEntry candidate = new QueueEntry(ids.queueEntry(), booking, booking.getService(), 2);
        LocalDateTime staleTimestamp = LocalDateTime.now(clock).minusDays(1);
        candidate.setQueueStatus(QueueStatus.COMPLETED);
        candidate.setJoinedAt(staleTimestamp);
        candidate.setCalledAt(staleTimestamp);
        candidate.setStartedAt(staleTimestamp);
        candidate.setCompletedAt(staleTimestamp);
        candidate.setEstimatedWaitMin(999);

        QueueEntry created = queueService.createQueueEntry(candidate);

        assertEquals(QueueStatus.WAITING, created.getQueueStatus());
        assertEquals(LocalDateTime.now(clock), created.getJoinedAt());
        assertNull(created.getCalledAt());
        assertNull(created.getStartedAt());
        assertNull(created.getCompletedAt());
        assertEquals(booking.getService().getEstimatedDurationMin(), created.getEstimatedWaitMin());
        assertSame(booking, created.getBooking());
        assertSame(booking.getService(), created.getService());
    }

    @Test
    void concurrentCreationAllowsExactlyOneActiveEntryForBooking() throws Exception {
        Booking booking = createConfirmedBooking();
        int attempts = 8;
        List<String> queueIds = IntStream.range(0, attempts).mapToObj(index -> ids.queueEntry()).toList();
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = queueIds.stream().map(queueId -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent start timed out");
                try {
                    queueService.createQueueEntry(queueId, booking.getBookingId(),
                            booking.getService().getServiceId(), 1);
                    return "SUCCESS";
                } catch (BusinessRuleViolationException exception) {
                    return exception.getMessage();
                }
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) outcomes.add(future.get(5, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count());
            assertEquals(attempts - 1, outcomes.stream()
                    .filter("Booking already has an active queue entry"::equals).count());
            List<QueueEntry> active = queueRepository.findByBookingId(booking.getBookingId()).stream()
                    .filter(queueEntry -> queueEntry.getQueueStatus().isActive()).toList();
            assertEquals(1, active.size());
            assertSame(active.getFirst(), booking.getQueueEntry());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void duplicateQueueEntryIdDoesNotOverwriteOriginalOrAttachSecondBooking() {
        Booking originalBooking = createConfirmedBooking(TestDates.futureDays(3));
        Booking secondBooking = createConfirmedBooking(TestDates.futureDays(4));
        String duplicateId = ids.queueEntry();
        QueueEntry original = queueService.createQueueEntry(
                new QueueEntry(duplicateId, originalBooking, originalBooking.getService(), 1));

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> queueService.createQueueEntry(
                        new QueueEntry(duplicateId, secondBooking, secondBooking.getService(), 1)));

        assertEquals("Queue entry ID already exists", exception.getMessage());
        assertEquals(1, queueRepository.findAll().size());
        assertSame(original, queueRepository.findById(duplicateId).orElseThrow());
        assertSame(original, originalBooking.getQueueEntry());
        assertNull(secondBooking.getQueueEntry());
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
