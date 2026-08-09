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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueManagementServiceTest extends ServiceTestSupport {

    @Test
    void queueEntryCreationSucceedsWithServerManagedMetrics() {
        Booking booking = createConfirmedBooking();
        String queueId = ids.queueEntry();
        QueueEntry candidate = new QueueEntry(queueId, booking, booking.getService(), 99);

        QueueEntry created = queueService.createQueueEntry(candidate);

        assertEquals(queueId, created.getQueueEntryId());
        assertEquals(QueueStatus.WAITING, created.getQueueStatus());
        assertEquals(1, created.getPosition());
        assertEquals(0, created.getEstimatedWaitMin());
        assertNotSame(candidate, created);
        assertEquals(booking.getBookingId(), created.getBooking().getBookingId());
        assertEquals(booking.getService().getServiceId(), created.getService().getServiceId());
        assertSame(candidate, booking.getQueueEntry());
    }

    @Test
    void serverAssignsConsecutivePositionsAndCumulativeWaits() {
        Booking firstBooking = confirmedBookingWithDuration(10, 1);
        Booking secondBooking = confirmedBookingWithDuration(25, 2);
        Booking thirdBooking = confirmedBookingWithDuration(15, 3);

        QueueEntry first = createQueueEntry(firstBooking);
        QueueEntry second = createQueueEntry(secondBooking);
        QueueEntry third = createQueueEntry(thirdBooking);

        assertQueueMetrics(first, 1, 0);
        assertQueueMetrics(second, 2, 10);
        assertQueueMetrics(third, 3, 35);
        assertQueueOrder(queueService.findAll(), first, second, third);
    }

    @Test
    void configuredFallbackDurationIsUsedForActivePredecessorWithoutDuration() {
        Booking firstBooking = confirmedBookingWithDuration(0, 4);
        Booking secondBooking = confirmedBookingWithDuration(25, 5);

        QueueEntry first = createQueueEntry(firstBooking);
        QueueEntry second = createQueueEntry(secondBooking);

        assertQueueMetrics(first, 1, 0);
        assertQueueMetrics(second, 2, 10);
    }

    @Test
    void queueLifecycleTimestampsUseSuppliedClock() {
        QueueEntry queueEntry = createQueueEntry(createConfirmedBooking());
        LocalDateTime expected = LocalDateTime.now(clock);

        assertEquals(expected, queueEntry.getJoinedAt());
        assertEquals(expected, queueService.callNext(queueEntry.getQueueEntryId()).getCalledAt());
        assertEquals(expected, queueService.startService(queueEntry.getQueueEntryId()).getStartedAt());
        assertEquals(expected, queueService.completeQueueEntry(queueEntry.getQueueEntryId()).getCompletedAt());
    }

    @Test
    void queueLifecycleSynchronizesAssociatedBookingAtStartAndCompletion() {
        QueueEntry queueEntry = createQueueEntry(createConfirmedBooking());
        Booking booking = queueEntry.getBooking();

        QueueEntry called = queueService.callNext(queueEntry.getQueueEntryId());
        assertEquals(QueueStatus.CALLED, called.getQueueStatus());
        assertEquals(BookingStatus.CONFIRMED, called.getBooking().getStatus());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());

        QueueEntry started = queueService.startService(queueEntry.getQueueEntryId());
        assertEquals(QueueStatus.IN_PROGRESS, started.getQueueStatus());
        assertEquals(BookingStatus.IN_SERVICE, started.getBooking().getStatus());
        assertEquals(BookingStatus.IN_SERVICE, bookingRepository.findById(booking.getBookingId()).orElseThrow().getStatus());

        QueueEntry completed = queueService.completeQueueEntry(queueEntry.getQueueEntryId());
        assertEquals(QueueStatus.COMPLETED, completed.getQueueStatus());
        assertEquals(0, completed.getEstimatedWaitMin());
        assertEquals(BookingStatus.COMPLETED, completed.getBooking().getStatus());
        assertEquals(BookingStatus.COMPLETED,
                bookingRepository.findById(booking.getBookingId()).orElseThrow().getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CREATED", "CANCELLED", "IN_SERVICE", "COMPLETED"})
    void startRejectsEveryIncompatibleBookingStateWithoutPartialMutation(BookingStatus bookingStatus) {
        QueueEntry queueEntry = createQueueEntry(createConfirmedBooking());
        queueService.callNext(queueEntry.getQueueEntryId());
        Booking booking = queueEntry.getBooking();
        booking.setStatus(bookingStatus);
        assertTrue(bookingRepository.update(booking));

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> queueService.startService(queueEntry.getQueueEntryId()));

        assertEquals("Booking must be confirmed before service can start", exception.getMessage());
        assertEquals(QueueStatus.CALLED, queueEntry.getQueueStatus());
        assertNull(queueEntry.getStartedAt());
        assertEquals(bookingStatus, booking.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CONFIRMED", "CANCELLED", "COMPLETED"})
    void completionRejectsEveryIncompatibleBookingStateWithoutPartialMutation(BookingStatus bookingStatus) {
        QueueEntry queueEntry = createQueueEntry(createConfirmedBooking());
        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());
        Booking booking = queueEntry.getBooking();
        booking.setStatus(bookingStatus);
        assertTrue(bookingRepository.update(booking));
        int originalPosition = queueEntry.getPosition();
        int originalWait = queueEntry.getEstimatedWaitMin();

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> queueService.completeQueueEntry(queueEntry.getQueueEntryId()));

        assertEquals("Booking must be in service before queue completion", exception.getMessage());
        assertEquals(QueueStatus.IN_PROGRESS, queueEntry.getQueueStatus());
        assertNull(queueEntry.getCompletedAt());
        assertEquals(originalPosition, queueEntry.getPosition());
        assertEquals(originalWait, queueEntry.getEstimatedWaitMin());
        assertEquals(bookingStatus, booking.getStatus());
    }

    @Test
    void queueMetricsRejectInvalidValues() {
        QueueEntry queueEntry = new QueueEntry();

        assertThrows(IllegalArgumentException.class, () -> queueEntry.updateQueueMetrics(0, 0));
        assertThrows(IllegalArgumentException.class, () -> queueEntry.updateQueueMetrics(1, -1));
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
                () -> queueService.createQueueEntry(new QueueEntry(ids.queueEntry(), missing, service)));
    }

    @Test
    void createQueueEntryRejectsUnknownService() {
        Booking booking = createConfirmedBooking();
        Service missing = new Service(ids.service(), "Missing", "desc", BigDecimal.TEN, 30);

        assertThrows(ResourceNotFoundException.class,
                () -> queueService.createQueueEntry(new QueueEntry(ids.queueEntry(), booking, missing)));
    }

    @Test
    void createQueueEntryRejectsMismatchedBookingService() {
        Booking booking = createConfirmedBooking();
        Service other = createService();

        assertThrows(BusinessRuleViolationException.class,
                () -> queueService.createQueueEntry(new QueueEntry(ids.queueEntry(), booking, other)));
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CREATED", "CANCELLED", "IN_SERVICE", "COMPLETED"})
    void createQueueEntryRejectsNonConfirmedBookings(BookingStatus status) {
        Booking booking = createSavedBooking(TestDates.futureDays(6), status);

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> queueService.createQueueEntry(
                        new QueueEntry(ids.queueEntry(), booking, booking.getService())));

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
                        new QueueEntry(ids.queueEntry(), booking, booking.getService())));

        assertEquals("Inactive service cannot join the queue", exception.getMessage());
        assertTrue(queueRepository.findAll().isEmpty());
        assertNull(booking.getQueueEntry());
    }

    @ParameterizedTest
    @EnumSource(value = QueueStatus.class, names = {"WAITING", "CALLED", "IN_PROGRESS"})
    void createQueueEntryRejectsSecondActiveEntry(QueueStatus activeStatus) {
        Booking booking = createConfirmedBooking();
        QueueEntry existing = createQueueEntry(booking);
        existing.setQueueStatus(activeStatus);
        assertTrue(queueRepository.update(existing));

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> queueService.createQueueEntry(
                        new QueueEntry(ids.queueEntry(), booking, booking.getService())));

        assertEquals("Booking already has an active queue entry", exception.getMessage());
        assertEquals(1, queueRepository.findByBookingId(booking.getBookingId()).size());
        assertSame(existing, booking.getQueueEntry());
    }

    @Test
    void queueEntryCreationNormalizesServerControlledStateAndPosition() {
        Booking booking = createConfirmedBooking();
        QueueEntry candidate = new QueueEntry(ids.queueEntry(), booking, booking.getService(), 99);
        LocalDateTime staleTimestamp = LocalDateTime.now(clock).minusDays(1);
        candidate.setQueueStatus(QueueStatus.COMPLETED);
        candidate.setJoinedAt(staleTimestamp);
        candidate.setCalledAt(staleTimestamp);
        candidate.setStartedAt(staleTimestamp);
        candidate.setCompletedAt(staleTimestamp);
        candidate.setEstimatedWaitMin(999);

        QueueEntry created = queueService.createQueueEntry(candidate);

        assertEquals(QueueStatus.WAITING, created.getQueueStatus());
        assertEquals(1, created.getPosition());
        assertEquals(0, created.getEstimatedWaitMin());
        assertEquals(LocalDateTime.now(clock), created.getJoinedAt());
        assertNull(created.getCalledAt());
        assertNull(created.getStartedAt());
        assertNull(created.getCompletedAt());
        assertNotSame(candidate, created);
        assertEquals(booking.getBookingId(), created.getBooking().getBookingId());
        assertEquals(booking.getService().getServiceId(), created.getService().getServiceId());
        assertSame(candidate, booking.getQueueEntry());
    }

    @Test
    void completionRemovesEntryFromActiveMetricsAndClosesGap() {
        QueueEntry first = createQueueEntry(confirmedBookingWithDuration(10, 7));
        QueueEntry second = createQueueEntry(confirmedBookingWithDuration(25, 8));
        QueueEntry third = createQueueEntry(confirmedBookingWithDuration(15, 9));

        queueService.callNext(first.getQueueEntryId());
        queueService.startService(first.getQueueEntryId());
        QueueEntry completed = queueService.completeQueueEntry(first.getQueueEntryId());

        assertEquals(QueueStatus.COMPLETED, completed.getQueueStatus());
        assertEquals(0, completed.getEstimatedWaitMin());
        assertQueueMetrics(second, 1, 0);
        assertQueueMetrics(third, 2, 25);
        assertQueueOrder(queueService.findAll(), second, third, completed);
    }

    @Test
    void deletionDetachesBookingAndRebalancesRemainingQueue() {
        QueueEntry first = createQueueEntry(confirmedBookingWithDuration(10, 10));
        QueueEntry second = createQueueEntry(confirmedBookingWithDuration(25, 11));
        QueueEntry third = createQueueEntry(confirmedBookingWithDuration(15, 12));
        Booking deletedBooking = second.getBooking();

        queueService.deleteQueueEntry(second.getQueueEntryId());

        assertFalse(queueRepository.existsById(second.getQueueEntryId()));
        assertNull(deletedBooking.getQueueEntry());
        assertQueueMetrics(first, 1, 0);
        assertQueueMetrics(third, 2, 10);
        assertQueueOrder(queueService.findAll(), first, third);
    }

    @Test
    void manualMovementRebalancesEveryAffectedPositionAndWait() {
        QueueEntry first = createQueueEntry(confirmedBookingWithDuration(10, 13));
        QueueEntry second = createQueueEntry(confirmedBookingWithDuration(20, 14));
        QueueEntry third = createQueueEntry(confirmedBookingWithDuration(30, 15));

        QueueEntry moveResponse = queueService.updatePosition(third.getQueueEntryId(), 1);
        assertNotSame(third, moveResponse);
        assertQueueMetrics(moveResponse, 1, 0);
        assertQueueMetrics(third, 1, 0);
        assertQueueMetrics(first, 2, 30);
        assertQueueMetrics(second, 3, 40);
        assertQueueOrder(queueService.findAll(), third, first, second);

        queueService.updatePosition(third.getQueueEntryId(), 3);
        assertQueueMetrics(moveResponse, 1, 0);
        assertQueueMetrics(first, 1, 0);
        assertQueueMetrics(second, 2, 10);
        assertQueueMetrics(third, 3, 30);

        queueService.updatePosition(second.getQueueEntryId(), 2);
        assertQueueOrder(queueService.findAll(), first, second, third);
    }

    @Test
    void queueReadsReturnDetachedDeepSnapshots() {
        QueueEntry first = createQueueEntry(confirmedBookingWithDuration(10, 16));
        QueueEntry second = createQueueEntry(confirmedBookingWithDuration(20, 17));
        List<QueueEntry> snapshot = queueService.findAll();

        assertNotSame(first, snapshot.getFirst());
        assertNotSame(first.getBooking(), snapshot.getFirst().getBooking());
        assertNotSame(first.getService(), snapshot.getFirst().getService());
        assertQueueMetrics(snapshot.getFirst(), 1, 0);
        assertQueueMetrics(snapshot.get(1), 2, 10);

        Service firstService = first.getService();
        catalogService.updateService(firstService.getServiceId(), firstService.getServiceName(),
                firstService.getDescription(), firstService.getPrice(), 30);
        queueService.updatePosition(second.getQueueEntryId(), 1);

        assertQueueMetrics(snapshot.getFirst(), 1, 0);
        assertQueueMetrics(snapshot.get(1), 2, 10);
        assertEquals(10, snapshot.getFirst().getService().getEstimatedDurationMin());
        assertQueueMetrics(queueRepository.findById(second.getQueueEntryId()).orElseThrow(), 1, 0);
        assertQueueMetrics(queueRepository.findById(first.getQueueEntryId()).orElseThrow(), 2, 20);
    }

    @Test
    void manualMovementRejectsPositionBeyondActiveQueueSize() {
        QueueEntry queueEntry = createSavedQueueEntry();

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> queueService.updatePosition(queueEntry.getQueueEntryId(), 2));

        assertEquals("Queue position exceeds active queue size", exception.getMessage());
        assertQueueMetrics(queueEntry, 1, 0);
    }

    @ParameterizedTest
    @EnumSource(value = QueueStatus.class, names = {"CALLED", "IN_PROGRESS", "COMPLETED", "EXITED"})
    void manualMovementRejectsEveryNonWaitingStatus(QueueStatus status) {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueEntry.setQueueStatus(status);
        assertTrue(queueRepository.update(queueEntry));

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> queueService.updatePosition(queueEntry.getQueueEntryId(), 1));

        assertEquals("Only waiting queue entries can be repositioned", exception.getMessage());
    }

    @Test
    void manualMovementRejectsUnknownEntry() {
        assertThrows(ResourceNotFoundException.class, () -> queueService.updatePosition(ids.queueEntry(), 1));
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
                            booking.getService().getServiceId());
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
            List<QueueEntry> active = queueRepository.findActiveOrdered();
            assertEquals(1, active.size());
            assertQueueMetrics(active.getFirst(), 1, 0);
            assertSame(active.getFirst(), booking.getQueueEntry());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentDistinctBookingsReceiveUniqueConsecutivePositions() throws Exception {
        int attempts = 8;
        List<Booking> bookings = IntStream.range(0, attempts)
                .mapToObj(index -> confirmedBookingWithDuration(10 + index, 20 + index))
                .toList();
        List<String> queueIds = IntStream.range(0, attempts).mapToObj(index -> ids.queueEntry()).toList();
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<QueueEntry>> futures = IntStream.range(0, attempts)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Concurrent start timed out");
                        }
                        Booking booking = bookings.get(index);
                        return queueService.createQueueEntry(queueIds.get(index), booking.getBookingId(),
                                booking.getService().getServiceId());
                    })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<QueueEntry> future : futures) assertNotNull(future.get(5, TimeUnit.SECONDS));

            List<QueueEntry> active = queueRepository.findActiveOrdered();
            assertEquals(attempts, active.size());
            assertEquals(IntStream.rangeClosed(1, attempts).boxed().toList(),
                    active.stream().map(QueueEntry::getPosition).toList());
            assertEquals(attempts, active.stream().map(QueueEntry::getPosition).distinct().count());
            bookings.forEach(booking -> {
                assertNotNull(booking.getQueueEntry());
                assertSame(booking, booking.getQueueEntry().getBooking());
            });
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void duplicateQueueEntryIdDoesNotOverwriteOriginalOrAttachSecondBooking() {
        Booking originalBooking = createConfirmedBooking(TestDates.futureDays(30));
        Booking secondBooking = createConfirmedBooking(TestDates.futureDays(31));
        String duplicateId = ids.queueEntry();
        QueueEntry originalResponse = queueService.createQueueEntry(
                new QueueEntry(duplicateId, originalBooking, originalBooking.getService()));
        QueueEntry original = queueRepository.findById(originalResponse.getQueueEntryId()).orElseThrow();

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> queueService.createQueueEntry(
                        new QueueEntry(duplicateId, secondBooking, secondBooking.getService())));

        assertEquals("Queue entry ID already exists", exception.getMessage());
        assertEquals(1, queueRepository.findAll().size());
        assertNotSame(originalResponse, original);
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

    private Booking confirmedBookingWithDuration(int duration, int futureDay) {
        Booking booking = createConfirmedBooking(TestDates.futureDays(futureDay));
        booking.getService().setEstimatedDurationMin(duration);
        assertTrue(serviceRepository.update(booking.getService()));
        return booking;
    }

    private QueueEntry createQueueEntry(Booking booking) {
        QueueEntry created = queueService.createQueueEntry(ids.queueEntry(), booking.getBookingId(),
                booking.getService().getServiceId());
        return queueRepository.findById(created.getQueueEntryId()).orElseThrow();
    }

    private void assertQueueMetrics(QueueEntry queueEntry, int position, int estimatedWaitMin) {
        assertEquals(position, queueEntry.getPosition());
        assertEquals(estimatedWaitMin, queueEntry.getEstimatedWaitMin());
    }

    private void assertQueueOrder(List<QueueEntry> actual, QueueEntry... expected) {
        assertEquals(List.of(expected).stream().map(QueueEntry::getQueueEntryId).toList(),
                actual.stream().map(QueueEntry::getQueueEntryId).toList());
    }

    private QueueEntry completedQueueEntry() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());
        return queueService.completeQueueEntry(queueEntry.getQueueEntryId());
    }
}
