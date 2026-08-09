package com.carwash.service;

import com.carwash.config.BookingPolicyProperties;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookingManagementServiceTest extends ServiceTestSupport {

    @Test
    void bookingCreationSucceeds() {
        Booking booking = newBookingWithFixture(TestDates.future());
        assertEquals(booking.getBookingId(), bookingService.createBooking(booking).getBookingId());
    }

    @Test
    void bookingCreationFailsWhenPast() {
        Booking booking = newBookingWithFixture(TestDates.past());
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsUnknownUser() {
        User missingUser = User.withEncodedPassword(ids.user(), "Missing", ids.emailFor(ids.user()), "123", "hash", null);
        Vehicle vehicle = new Vehicle(ids.vehicle(), ids.plate(), "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = createService();
        Booking booking = new Booking(ids.booking(), missingUser, vehicle, service, TestDates.future(), "none");
        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsUnknownVehicle() {
        User user = registerUser();
        Service service = createService();
        Vehicle missing = new Vehicle(ids.vehicle(), ids.plate(), "Sedan", "Toyota", "Corolla", "Blue", "");
        Booking booking = new Booking(ids.booking(), user, missing, service, TestDates.future(), "none");
        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsUnknownService() {
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        Service missing = new Service(ids.service(), "Missing", "desc", BigDecimal.TEN, 30);
        Booking booking = new Booking(ids.booking(), user, vehicle, missing, TestDates.future(), "none");
        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsInactiveService() {
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        Service service = createService();
        service.deactivate();
        Booking booking = new Booking(ids.booking(), user, vehicle, service, TestDates.future(), "none");
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsVehicleOwnedByDifferentUser() {
        User firstUser = registerUser();
        User secondUser = registerUser();
        Vehicle vehicle = createVehicle(secondUser);
        Service service = createService();
        Booking booking = new Booking(ids.booking(), firstUser, vehicle, service, TestDates.future(), "none");
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsPastScheduledDateTime() {
        Booking booking = newBookingWithFixture(TestDates.past());
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsFullTimeSlot() {
        LocalDateTime scheduled = TestDates.futureDays(3);
        bookingService.createBooking(newBookingWithFixture(scheduled));
        Booking overlapping = newBookingWithFixture(scheduled);
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class, () -> bookingService.createBooking(overlapping));
        assertTrue(exception.getMessage().contains("time slot"));
    }

    @Test
    void configuredCapacityAllowsTwoActiveBookingsButRejectsThird() {
        BookingManagementService capacityTwo = bookingServiceWithPolicy(2, Duration.ZERO);
        LocalDateTime scheduled = TestDates.futureDays(7);

        Booking first = newBookingWithFixture(scheduled);
        Booking second = newBookingWithFixture(scheduled);
        Booking third = newBookingWithFixture(scheduled);

        assertEquals(first.getBookingId(), capacityTwo.createBooking(first).getBookingId());
        assertEquals(second.getBookingId(), capacityTwo.createBooking(second).getBookingId());
        assertThrows(BusinessRuleViolationException.class, () -> capacityTwo.createBooking(third));
    }

    @Test
    void createBookingIgnoresCancelledBookingWhenCheckingSlotCapacity() {
        LocalDateTime scheduled = TestDates.futureDays(4);
        Booking cancelled = bookingService.createBooking(newBookingWithFixture(scheduled));
        bookingService.cancelBooking(cancelled.getBookingId(), cancelled.getUser().getUserId());
        Booking replacement = newBookingWithFixture(scheduled);
        assertEquals(replacement.getBookingId(), bookingService.createBooking(replacement).getBookingId());
    }

    @Test
    void createBookingRejectsSameVehicleConflictAtSameDateTime() {
        LocalDateTime scheduled = TestDates.futureDays(5);
        Booking existing = newBookingWithFixture(scheduled);
        bookingService.createBooking(existing);
        Booking conflicting = new Booking(ids.booking(), existing.getUser(), existing.getVehicle(),
                existing.getService(), scheduled, "conflict");
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class, () -> bookingService.createBooking(conflicting));
        assertTrue(exception.getMessage().contains("Customer vehicle"));
    }

    @Test
    void createBookingStillAllowsValidFutureBooking() {
        Booking booking = newBookingWithFixture(TestDates.futureDays(6));
        assertEquals(booking.getBookingId(), bookingService.createBooking(booking).getBookingId());
    }

    @Test
    void bookingConfirmationSucceeds() {
        Booking booking = createSavedBooking();
        assertEquals(BookingStatus.CONFIRMED,
                bookingService.confirmBooking(booking.getBookingId()).getStatus());
    }

    @Test
    void cancelledBookingCannotBeConfirmed() {
        Booking booking = createSavedBooking();
        bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId());
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.confirmBooking(booking.getBookingId()));
    }

    @Test
    void customerCanCancelOwnFutureBooking() {
        Booking booking = createSavedBooking();
        Booking cancelled = bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId());
        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertEquals(BookingStatus.CANCELLED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    @Test
    void cancellationRemovesWaitingQueueEntryAndRebalancesRemainingQueue() {
        Booking firstBooking = createConfirmedBooking(TestDates.futureDays(20));
        firstBooking.getService().setEstimatedDurationMin(10);
        assertTrue(serviceRepository.update(firstBooking.getService()));
        Booking secondBooking = createConfirmedBooking(TestDates.futureDays(21));
        QueueEntry first = canonicalQueue(queueService.createQueueEntry(
                ids.queueEntry(), firstBooking.getBookingId(), firstBooking.getService().getServiceId()));
        QueueEntry second = canonicalQueue(queueService.createQueueEntry(
                ids.queueEntry(), secondBooking.getBookingId(), secondBooking.getService().getServiceId()));
        assertEquals(2, second.getPosition());
        assertEquals(10, second.getEstimatedWaitMin());

        Booking cancelled = bookingService.cancelBooking(firstBooking.getBookingId(),
                firstBooking.getUser().getUserId());

        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertFalse(queueRepository.existsById(first.getQueueEntryId()));
        assertFalse(queueRepository.existsActiveByBookingId(firstBooking.getBookingId()));
        assertNull(firstBooking.getQueueEntry());
        assertEquals(1, second.getPosition());
        assertEquals(0, second.getEstimatedWaitMin());
        assertEquals(List.of("BOOKING_CONFIRMED", "BOOKING_CANCELLED"),
                notificationRepository.findByBookingId(firstBooking.getBookingId()).stream()
                        .map(notification -> notification.getType()).toList());
    }

    @Test
    void cancellationRemovesCalledQueueEntryWithoutChangingPublicDeleteRule() {
        Booking booking = createConfirmedBooking(TestDates.futureDays(22));
        QueueEntry queueEntry = canonicalQueue(queueService.createQueueEntry(
                ids.queueEntry(), booking.getBookingId(), booking.getService().getServiceId()));
        queueService.callNext(queueEntry.getQueueEntryId());

        assertThrows(BusinessRuleViolationException.class,
                () -> queueService.deleteQueueEntry(queueEntry.getQueueEntryId()));
        Booking cancelled = bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId());

        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertFalse(queueRepository.existsById(queueEntry.getQueueEntryId()));
        assertNull(booking.getQueueEntry());
    }

    @Test
    void inServiceBookingCancellationIsExplicitlyRejectedWithoutNotificationOrMutation() {
        Booking booking = createConfirmedBooking(TestDates.futureDays(23));
        QueueEntry queueEntry = canonicalQueue(queueService.createQueueEntry(
                ids.queueEntry(), booking.getBookingId(), booking.getService().getServiceId()));
        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());
        int notificationsBefore = notificationRepository.findByBookingId(booking.getBookingId()).size();

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId()));

        assertEquals("Booking cannot be cancelled while service is in progress", exception.getMessage());
        assertEquals(BookingStatus.IN_SERVICE, booking.getStatus());
        assertEquals(QueueStatus.IN_PROGRESS, queueEntry.getQueueStatus());
        assertSame(queueEntry, booking.getQueueEntry());
        assertEquals(notificationsBefore, notificationRepository.findByBookingId(booking.getBookingId()).size());
    }

    @Test
    void activeQueuedBookingCannotBeUpdated() {
        Booking booking = createConfirmedBooking(TestDates.futureDays(24));
        QueueEntry queueEntry = canonicalQueue(queueService.createQueueEntry(
                ids.queueEntry(), booking.getBookingId(), booking.getService().getServiceId()));
        Vehicle originalVehicle = booking.getVehicle();
        Service originalService = booking.getService();
        LocalDateTime originalSchedule = booking.getScheduledDateTime();

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.updateBooking(booking.getBookingId(), originalVehicle.getVehicleId(),
                        originalService.getServiceId(), TestDates.futureDays(25), "changed"));

        assertEquals("Booking cannot be updated while it has an active queue entry", exception.getMessage());
        assertSame(originalVehicle, booking.getVehicle());
        assertSame(originalService, booking.getService());
        assertEquals(originalSchedule, booking.getScheduledDateTime());
        assertSame(queueEntry, booking.getQueueEntry());
    }

    @Test
    void zeroCancellationWindowPreservesFutureBookingCancellation() {
        LocalDateTime scheduled = LocalDateTime.now(clock).plusMinutes(1);
        Booking booking = bookingService.createBooking(newBookingWithFixture(scheduled));

        assertEquals(BookingStatus.CANCELLED,
                bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId()).getStatus());
    }

    @Test
    void configuredCancellationWindowAllowsBeforeCutoffAndRejectsAfterCutoff() {
        BookingManagementService twoHourWindow = bookingServiceWithPolicy(1, Duration.ofHours(2));
        LocalDateTime now = LocalDateTime.now(clock);
        Booking outsideWindow = twoHourWindow.createBooking(newBookingWithFixture(now.plusHours(3)));
        Booking insideWindow = twoHourWindow.createBooking(newBookingWithFixture(now.plusHours(1)));

        assertEquals(BookingStatus.CANCELLED,
                twoHourWindow.cancelBooking(outsideWindow.getBookingId(), outsideWindow.getUser().getUserId()).getStatus());
        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> twoHourWindow.cancelBooking(insideWindow.getBookingId(), insideWindow.getUser().getUserId()));
        assertTrue(exception.getMessage().contains("window"));
    }

    @Test
    void cancellationIsRejectedAtExactConfiguredCutoff() {
        BookingManagementService twoHourWindow = bookingServiceWithPolicy(1, Duration.ofHours(2));
        Booking booking = twoHourWindow.createBooking(
                newBookingWithFixture(LocalDateTime.now(clock).plusHours(2)));

        assertThrows(BusinessRuleViolationException.class,
                () -> twoHourWindow.cancelBooking(booking.getBookingId(), booking.getUser().getUserId()));
    }

    @Test
    void futureValidationUsesSuppliedClockRatherThanHostTime() {
        Booking booking = newBookingWithFixture(LocalDateTime.now(clock).minusMinutes(1));

        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void customerCannotCancelAnotherCustomersBooking() {
        Booking booking = createSavedBooking();
        User other = registerUser();
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.cancelBooking(booking.getBookingId(), other.getUserId()));
        assertEquals(BookingStatus.CREATED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    @Test
    void customerCannotCancelPastBooking() {
        Booking booking = createSavedBooking();
        booking.setScheduledDateTime(TestDates.past());
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId()));
        assertEquals(BookingStatus.CREATED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    @Test
    void completedBookingCannotBeCancelled() {
        Booking booking = createSavedBooking();
        assertTrue(booking.confirm());
        assertTrue(booking.startService());
        assertTrue(booking.completeService());
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId()));
        assertEquals(BookingStatus.COMPLETED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    @Test
    void alreadyCancelledBookingCannotBeCancelledAgain() {
        Booking booking = createSavedBooking();
        bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId());
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId()));
        assertEquals(BookingStatus.CANCELLED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    private BookingManagementService bookingServiceWithPolicy(int capacity, Duration cancellationWindow) {
        return new BookingManagementService(
                bookingRepository, userRepository, vehicleRepository, serviceRepository,
                queueRepository, notificationRepository, notificationService, queueOrdering, coordinator,
                new BookingPolicyProperties(capacity, cancellationWindow), clock);
    }

    private QueueEntry canonicalQueue(QueueEntry response) {
        return queueRepository.findById(response.getQueueEntryId()).orElseThrow();
    }
}
