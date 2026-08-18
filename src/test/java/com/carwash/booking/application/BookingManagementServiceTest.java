package com.carwash.booking.application;

import com.carwash.testsupport.ServiceTestSupport;

import com.carwash.booking.application.BookingManagementService;
import com.carwash.notification.application.NotificationManagementService;

import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.notification.application.NotificationPolicyProperties;
import com.carwash.booking.domain.Booking;
import com.carwash.notification.domain.Notification;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.catalog.application.UpdateServiceOfferingCommand;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        Booking booking = new Booking(
                ids.booking(), missingUser, vehicle, ensureDefaultBranch(), createOffering(service),
                service, TestDates.future(), "none");
        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsUnknownVehicle() {
        User user = registerUser();
        Service service = createService();
        Vehicle missing = new Vehicle(ids.vehicle(), ids.plate(), "Sedan", "Toyota", "Corolla", "Blue", "");
        Booking booking = new Booking(
                ids.booking(), user, missing, ensureDefaultBranch(), createOffering(service),
                service, TestDates.future(), "none");
        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsUnknownOffering() {
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        Booking booking = new Booking(
                ids.booking(), user, vehicle, ensureDefaultBranch(), ids.offering(),
                null, TestDates.future(), "none");
        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsInactiveService() {
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        Service service = createService();
        String offeringId = createOffering(service);
        service.deactivate();
        serviceRepository.update(service);
        Booking booking = new Booking(
                ids.booking(), user, vehicle, ensureDefaultBranch(), offeringId,
                service, TestDates.future(), "none");
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsVehicleOwnedByDifferentUser() {
        User firstUser = registerUser();
        User secondUser = registerUser();
        Vehicle vehicle = createVehicle(secondUser);
        Service service = createService();
        Booking booking = new Booking(
                ids.booking(), firstUser, vehicle, ensureDefaultBranch(), createOffering(service),
                service, TestDates.future(), "none");
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
        Booking existing = bookingService.createBooking(newBookingWithFixture(scheduled));
        Booking overlapping = newBookingForScope(existing, scheduled);
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class, () -> bookingService.createBooking(overlapping));
        assertTrue(exception.getMessage().contains("time slot"));
    }

    @Test
    void offeringCapacityAllowsTwoActiveBookingsButRejectsThird() {
        BookingManagementService capacityTwo = bookingServiceWithPolicy(2, Duration.ZERO);
        LocalDateTime scheduled = TestDates.futureDays(7);

        Booking first = newBookingWithFixture(scheduled);
        serviceOfferingService.updateOffering(first.getServiceOfferingId(), new UpdateServiceOfferingCommand(
                first.getService().getPrice(), first.getService().getEstimatedDurationMin(), 2));
        Booking second = newBookingForScope(first, scheduled);
        Booking third = newBookingForScope(first, scheduled);

        assertEquals(first.getBookingId(), capacityTwo.createBooking(first).getBookingId());
        assertEquals(second.getBookingId(), capacityTwo.createBooking(second).getBookingId());
        assertThrows(BusinessRuleViolationException.class, () -> capacityTwo.createBooking(third));
    }

    @Test
    void createBookingIgnoresCancelledBookingWhenCheckingSlotCapacity() {
        LocalDateTime scheduled = TestDates.futureDays(4);
        Booking cancelled = bookingService.createBooking(newBookingWithFixture(scheduled));
        bookingService.cancelBooking(cancelled.getBookingId(), cancelled.getUser().getUserId());
        Booking replacement = newBookingForScope(cancelled, scheduled);
        assertEquals(replacement.getBookingId(), bookingService.createBooking(replacement).getBookingId());
    }

    @Test
    void createBookingRejectsSameVehicleConflictAtSameDateTime() {
        LocalDateTime scheduled = TestDates.futureDays(5);
        Booking existing = newBookingWithFixture(scheduled);
        bookingService.createBooking(existing);
        Booking conflicting = new Booking(ids.booking(), existing.getUser(), existing.getVehicle(),
                existing.getBranchId(), existing.getServiceOfferingId(), existing.getService(), scheduled, "conflict");
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
    void bookingCreationRejectsOutsideHoursAndOffGridStarts() {
        LocalDate date = TestDates.futureDays(8).toLocalDate();

        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.createBooking(newBookingWithFixture(date.atTime(7, 30))));
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.createBooking(newBookingWithFixture(date.atTime(17, 0))));
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.createBooking(newBookingWithFixture(date.atTime(9, 10))));
        assertEquals(date.atTime(9, 30),
                bookingService.createBooking(newBookingWithFixture(date.atTime(9, 30))).getScheduledDateTime());
    }

    @Test
    void bookingCreationRequiresServiceToFinishByClosing() {
        LocalDate date = TestDates.futureDays(9).toLocalDate();
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        Service hourService = catalogService.createService(new Service(
                ids.service(), "Hour wash", "test", BigDecimal.TEN, 60));
        String offeringId = createOffering(hourService);

        Booking tooLate = new Booking(
                ids.booking(), user, vehicle, ensureDefaultBranch(), offeringId,
                hourService, date.atTime(16, 30), "too late");
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(tooLate));

        Booking valid = new Booking(
                ids.booking(), user, vehicle, ensureDefaultBranch(), offeringId,
                hourService, date.atTime(16, 0), "valid");
        assertEquals(date.atTime(16, 0), bookingService.createBooking(valid).getScheduledDateTime());
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
        serviceOfferingService.updateOffering(firstBooking.getServiceOfferingId(),
                new UpdateServiceOfferingCommand(firstBooking.getService().getPrice(), 10, 2));
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
        queueService.callQueueEntry(queueEntry.getQueueEntryId());

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
        queueService.callQueueEntry(queueEntry.getQueueEntryId());
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
                        originalService.getServiceId(), "changed"));

        assertEquals("Booking cannot be updated while it has an active queue entry", exception.getMessage());
        assertSame(originalVehicle, booking.getVehicle());
        assertSame(originalService, booking.getService());
        assertEquals(originalSchedule, booking.getScheduledDateTime());
        assertSame(queueEntry, booking.getQueueEntry());
    }

    @Test
    void createdBookingRescheduleChangesOnlyScheduleAndCreatesUpdatedNotification() {
        Booking booking = createSavedBooking(TestDates.futureDays(40), BookingStatus.CREATED);
        User owner = booking.getUser();
        Vehicle vehicle = booking.getVehicle();
        Service service = booking.getService();
        LocalDateTime target = TestDates.futureDays(41);

        Booking rescheduled = bookingService.rescheduleBooking(booking.getBookingId(), target);

        assertEquals(target, rescheduled.getScheduledDateTime());
        assertEquals(BookingStatus.CREATED, rescheduled.getStatus());
        assertSame(owner, rescheduled.getUser());
        assertSame(vehicle, rescheduled.getVehicle());
        assertSame(service, rescheduled.getService());
        assertEquals(target, bookingRepository.findById(booking.getBookingId()).orElseThrow().getScheduledDateTime());
        Notification notification = notificationRepository.findByBookingId(booking.getBookingId()).getFirst();
        assertEquals("BOOKING_RESCHEDULED", notification.getType());
        assertEquals(target, notification.getBooking().getScheduledDateTime());
        assertTrue(notification.getMessage().contains(target.toString()));
    }

    @Test
    void confirmedBookingReschedulePreservesConfirmationAndNotificationOrder() {
        Booking booking = createConfirmedBooking(TestDates.futureDays(42));
        LocalDateTime target = TestDates.futureDays(43);

        Booking rescheduled = bookingService.rescheduleBooking(booking.getBookingId(), target);

        assertEquals(target, rescheduled.getScheduledDateTime());
        assertEquals(BookingStatus.CONFIRMED, rescheduled.getStatus());
        assertEquals(List.of("BOOKING_CONFIRMED", "BOOKING_RESCHEDULED"),
                notificationRepository.findByBookingId(booking.getBookingId()).stream()
                        .map(Notification::getType).toList());
    }

    @Test
    void rescheduleRejectsPastNewTimeWithoutMutationOrNotification() {
        Booking booking = createSavedBooking(TestDates.futureDays(44), BookingStatus.CREATED);
        LocalDateTime original = booking.getScheduledDateTime();

        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.rescheduleBooking(booking.getBookingId(), LocalDateTime.now(clock).minusMinutes(1)));

        assertEquals(original, booking.getScheduledDateTime());
        assertTrue(notificationRepository.findByBookingId(booking.getBookingId()).isEmpty());
    }

    @Test
    void rescheduleRejectsBookingWhoseCurrentScheduleIsNotFuture() {
        Booking booking = createSavedBooking(TestDates.futureDays(45), BookingStatus.CREATED);
        booking.setScheduledDateTime(LocalDateTime.now(clock));

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.rescheduleBooking(booking.getBookingId(), TestDates.futureDays(46)));

        assertEquals("Only future bookings can be rescheduled", exception.getMessage());
        assertEquals(LocalDateTime.now(clock), booking.getScheduledDateTime());
    }

    @Test
    void rescheduleUsesConfiguredCutoffBeforeInsideAndAtBoundary() {
        BookingManagementService twoHourWindow = bookingServiceWithPolicy(1, Duration.ofHours(2));
        replaceFullWeekOperatingHours(ensureDefaultBranch(), LocalTime.of(8, 0), LocalTime.of(23, 0));
        LocalDateTime now = branchLocalNow();
        Booking beforeCutoff = twoHourWindow.createBooking(newBookingWithFixture(now.plusHours(3)));
        Booking insideCutoff = twoHourWindow.createBooking(newBookingWithFixture(now.plusHours(1)));
        Booking atCutoff = twoHourWindow.createBooking(newBookingWithFixture(now.plusHours(2)));

        assertEquals(now.plusHours(4),
                twoHourWindow.rescheduleBooking(beforeCutoff.getBookingId(), now.plusHours(4)).getScheduledDateTime());
        assertEquals("Booking rescheduling window has closed", assertThrows(BusinessRuleViolationException.class,
                () -> twoHourWindow.rescheduleBooking(insideCutoff.getBookingId(), now.plusHours(5))).getMessage());
        assertEquals("Booking rescheduling window has closed", assertThrows(BusinessRuleViolationException.class,
                () -> twoHourWindow.rescheduleBooking(atCutoff.getBookingId(), now.plusHours(6))).getMessage());
    }

    @Test
    void rescheduleCutoffDoesNotImposeMinimumLeadTimeOnNewSchedule() {
        BookingManagementService twoHourWindow = bookingServiceWithPolicy(1, Duration.ofHours(2));
        replaceFullWeekOperatingHours(ensureDefaultBranch(), LocalTime.of(8, 0), LocalTime.of(23, 0));
        LocalDateTime now = branchLocalNow();
        Booking booking = twoHourWindow.createBooking(newBookingWithFixture(now.plusDays(4)));

        assertEquals(now.plusHours(1),
                twoHourWindow.rescheduleBooking(booking.getBookingId(), now.plusHours(1)).getScheduledDateTime());
    }

    @Test
    void rescheduleRejectsFullTargetSlotAndPreservesBothBookings() {
        LocalDateTime original = TestDates.futureDays(47);
        LocalDateTime target = TestDates.futureDays(48);
        Booking moving = bookingService.createBooking(newBookingWithFixture(original));
        Booking occupying = bookingService.createBooking(newBookingForScope(moving, target));

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.rescheduleBooking(moving.getBookingId(), target));

        assertEquals("Booking time slot is already full", exception.getMessage());
        assertEquals(original, moving.getScheduledDateTime());
        assertEquals(target, occupying.getScheduledDateTime());
    }

    @Test
    void rescheduleRejectsSameCustomerVehicleConflictAndPreservesOriginalSchedule() {
        LocalDateTime original = TestDates.futureDays(49);
        LocalDateTime target = TestDates.futureDays(50);
        Booking moving = bookingService.createBooking(newBookingWithFixture(original));
        Booking conflicting = new Booking(
                ids.booking(), moving.getUser(), moving.getVehicle(), moving.getBranchId(),
                moving.getServiceOfferingId(), moving.getService(), target, "conflict");
        bookingService.createBooking(conflicting);

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.rescheduleBooking(moving.getBookingId(), target));

        assertTrue(exception.getMessage().contains("Customer vehicle"));
        assertEquals(original, moving.getScheduledDateTime());
    }

    @Test
    void rescheduleExcludesTheBookingItselfFromTargetCapacity() {
        Booking booking = createSavedBooking(TestDates.futureDays(51), BookingStatus.CREATED);

        assertEquals(booking.getScheduledDateTime(),
                bookingService.rescheduleBooking(booking.getBookingId(), booking.getScheduledDateTime())
                        .getScheduledDateTime());
    }

    @Test
    void rescheduleRejectsInactiveCurrentService() {
        Booking booking = createSavedBooking(TestDates.futureDays(52), BookingStatus.CREATED);
        LocalDateTime original = booking.getScheduledDateTime();
        catalogService.deactivateService(booking.getService().getServiceId());

        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.rescheduleBooking(booking.getBookingId(), TestDates.futureDays(53)));

        assertEquals(original, booking.getScheduledDateTime());
    }

    @Test
    void rescheduleRevalidatesCanonicalVehicleOwnership() {
        Booking booking = createSavedBooking(TestDates.futureDays(54), BookingStatus.CREATED);
        LocalDateTime original = booking.getScheduledDateTime();
        User other = registerUser();
        booking.getVehicle().setUserId(other.getUserId());
        assertTrue(vehicleRepository.update(booking.getVehicle()));

        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.rescheduleBooking(booking.getBookingId(), TestDates.futureDays(55)));

        assertEquals(original, booking.getScheduledDateTime());
    }

    @Test
    void rescheduleRejectsWaitingQueueWithoutChangingQueueMetrics() {
        Booking booking = createConfirmedBooking(TestDates.futureDays(56));
        QueueEntry queue = canonicalQueue(queueService.createQueueEntry(
                ids.queueEntry(), booking.getBookingId(), booking.getService().getServiceId()));
        LocalDateTime original = booking.getScheduledDateTime();
        int position = queue.getPosition();
        int eta = queue.getEstimatedWaitMin();

        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.rescheduleBooking(booking.getBookingId(), TestDates.futureDays(57)));

        assertEquals(original, booking.getScheduledDateTime());
        assertEquals(QueueStatus.WAITING, queue.getQueueStatus());
        assertEquals(position, queue.getPosition());
        assertEquals(eta, queue.getEstimatedWaitMin());
    }

    @Test
    void rescheduleRejectsCalledQueueAndPreservesConfirmedBooking() {
        Booking booking = createConfirmedBooking(TestDates.futureDays(58));
        QueueEntry queue = canonicalQueue(queueService.createQueueEntry(
                ids.queueEntry(), booking.getBookingId(), booking.getService().getServiceId()));
        queueService.callQueueEntry(queue.getQueueEntryId());
        LocalDateTime original = booking.getScheduledDateTime();

        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.rescheduleBooking(booking.getBookingId(), TestDates.futureDays(59)));

        assertEquals(original, booking.getScheduledDateTime());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(QueueStatus.CALLED, queue.getQueueStatus());
    }

    @Test
    void rescheduleRejectsInServiceBookingAndInProgressQueue() {
        Booking booking = createConfirmedBooking(TestDates.futureDays(60));
        QueueEntry queue = canonicalQueue(queueService.createQueueEntry(
                ids.queueEntry(), booking.getBookingId(), booking.getService().getServiceId()));
        queueService.callQueueEntry(queue.getQueueEntryId());
        queueService.startService(queue.getQueueEntryId());
        LocalDateTime original = booking.getScheduledDateTime();

        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.rescheduleBooking(booking.getBookingId(), TestDates.futureDays(61)));

        assertEquals(original, booking.getScheduledDateTime());
        assertEquals(BookingStatus.IN_SERVICE, booking.getStatus());
        assertEquals(QueueStatus.IN_PROGRESS, queue.getQueueStatus());
    }

    @Test
    void rescheduleRejectsCompletedAndCancelledBookingsWithoutNotification() {
        Booking completed = createSavedBooking(TestDates.futureDays(62), BookingStatus.COMPLETED);
        Booking cancelled = createSavedBooking(TestDates.futureDays(63), BookingStatus.CANCELLED);

        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.rescheduleBooking(completed.getBookingId(), TestDates.futureDays(64)));
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.rescheduleBooking(cancelled.getBookingId(), TestDates.futureDays(65)));
        assertTrue(notificationRepository.findByBookingId(completed.getBookingId()).isEmpty());
        assertTrue(notificationRepository.findByBookingId(cancelled.getBookingId()).isEmpty());
    }

    @Test
    void repositoryFailureRestoresOriginalScheduleAndCreatesNoNotification() {
        Booking booking = createSavedBooking(TestDates.futureDays(66), BookingStatus.CREATED);
        LocalDateTime original = booking.getScheduledDateTime();
        User owner = booking.getUser();
        Vehicle vehicle = booking.getVehicle();
        Service service = booking.getService();
        FailingUpdateBookingRepository failingRepository = new FailingUpdateBookingRepository(bookingRepository);
        BookingManagementService failingService = bookingServiceWithRepositories(
                failingRepository, notificationService, new BookingPolicyProperties(1, Duration.ZERO));
        failingRepository.failNextUpdate();

        assertThrows(IllegalStateException.class,
                () -> failingService.rescheduleBooking(booking.getBookingId(), TestDates.futureDays(67)));

        assertEquals(original, bookingRepository.findById(booking.getBookingId()).orElseThrow().getScheduledDateTime());
        assertEquals(BookingStatus.CREATED, booking.getStatus());
        assertSame(owner, booking.getUser());
        assertSame(vehicle, booking.getVehicle());
        assertSame(service, booking.getService());
        assertTrue(notificationRepository.findByBookingId(booking.getBookingId()).isEmpty());
    }

    @Test
    void notificationFailureDoesNotFailOrRollBackCommittedReschedule() {
        Booking booking = createSavedBooking(TestDates.futureDays(68), BookingStatus.CREATED);
        NotificationManagementService failingNotifications = new NotificationManagementService(
                notificationRepository, userRepository, bookingRepository, coordinator, notificationIds,
                new NotificationPolicyProperties(10), clock) {
            @Override
            public Notification createNotification(User user, Booking notificationBooking, String type, String message) {
                throw new IllegalStateException("notification unavailable");
            }
        };
        BookingManagementService service = bookingServiceWithRepositories(
                bookingRepository, failingNotifications, new BookingPolicyProperties(1, Duration.ZERO));
        LocalDateTime target = TestDates.futureDays(69);

        assertEquals(target, service.rescheduleBooking(booking.getBookingId(), target).getScheduledDateTime());
        assertEquals(target, bookingRepository.findById(booking.getBookingId()).orElseThrow().getScheduledDateTime());
        assertTrue(notificationRepository.findByBookingId(booking.getBookingId()).isEmpty());
    }

    @Test
    void concurrentReschedulesToFinalSlotAllowExactlyOneBooking() throws Exception {
        LocalDateTime firstOriginal = TestDates.futureDays(70);
        LocalDateTime secondOriginal = TestDates.futureDays(71);
        LocalDateTime target = TestDates.futureDays(72);
        Booking first = bookingService.createBooking(newBookingWithFixture(firstOriginal));
        Booking second = bookingService.createBooking(newBookingForScope(first, secondOriginal));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstResult = executor.submit(
                    () -> attemptConcurrentReschedule(first.getBookingId(), target, ready, start));
            Future<Boolean> secondResult = executor.submit(
                    () -> attemptConcurrentReschedule(second.getBookingId(), target, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            long successes = List.of(firstResult.get(5, TimeUnit.SECONDS), secondResult.get(5, TimeUnit.SECONDS))
                    .stream().filter(Boolean::booleanValue).count();

            assertEquals(1, successes);
            assertEquals(1, bookingRepository.findByScheduledDateTime(target).size());
            assertTrue(first.getScheduledDateTime().equals(target)
                    ^ second.getScheduledDateTime().equals(target));
            assertTrue(first.getScheduledDateTime().equals(firstOriginal)
                    || second.getScheduledDateTime().equals(secondOriginal));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void genericUpdateChangesAllowedFieldsButPreservesSchedule() {
        Booking booking = createSavedBooking(TestDates.futureDays(73), BookingStatus.CREATED);
        LocalDateTime original = booking.getScheduledDateTime();
        Vehicle alternate = createVehicle(booking.getUser());

        Booking updated = bookingService.updateBooking(
                booking.getBookingId(), alternate.getVehicleId(), booking.getServiceOfferingId(), "updated");

        assertEquals(original, updated.getScheduledDateTime());
        assertEquals(alternate.getVehicleId(), updated.getVehicle().getVehicleId());
        assertEquals("updated", updated.getSpecialRequest());
        assertEquals(BookingStatus.CREATED, updated.getStatus());
    }

    @Test
    void reschedulingUsesTheSharedGridWindowAndDurationRules() {
        Booking booking = createSavedBooking(TestDates.futureDays(74), BookingStatus.CREATED);
        LocalDateTime original = booking.getScheduledDateTime();
        LocalDate targetDate = TestDates.futureDays(75).toLocalDate();
        serviceOfferingService.updateOffering(booking.getServiceOfferingId(),
                new UpdateServiceOfferingCommand(booking.getService().getPrice(), 60, 2));

        for (LocalDateTime invalid : List.of(
                targetDate.atTime(7, 30),
                targetDate.atTime(9, 10),
                targetDate.atTime(16, 30))) {
            assertThrows(BusinessRuleViolationException.class,
                    () -> bookingService.rescheduleBooking(booking.getBookingId(), invalid));
            assertEquals(original, booking.getScheduledDateTime());
        }

        LocalDateTime valid = targetDate.atTime(9, 30);
        assertEquals(valid, bookingService.rescheduleBooking(booking.getBookingId(), valid).getScheduledDateTime());
    }

    @Test
    void genericOfferingUpdateRejectsDurationThatWouldFinishAfterClosingWithoutMutation() {
        LocalDateTime schedule = TestDates.futureDays(76).toLocalDate().atTime(16, 30);
        Booking booking = bookingService.createBooking(newBookingWithFixture(schedule));
        Service originalService = booking.getService();
        Service longService = catalogService.createService(new Service(
                ids.service(), "Long wash", "test", BigDecimal.TEN, 60));
        String longOfferingId = createOffering(longService);
        serviceOfferingService.updateOffering(longOfferingId,
                new UpdateServiceOfferingCommand(BigDecimal.TEN, 60, 2));

        assertThrows(BusinessRuleViolationException.class, () -> bookingService.updateBooking(
                booking.getBookingId(), booking.getVehicle().getVehicleId(), longOfferingId, "changed"));

        assertSame(originalService, booking.getService());
        assertEquals(schedule, booking.getScheduledDateTime());
        assertEquals("none", booking.getSpecialRequest());
    }

    @Test
    void zeroCancellationWindowPreservesFutureBookingCancellation() {
        LocalDateTime scheduled = branchLocalNow().plusMinutes(30);
        Booking booking = bookingService.createBooking(newBookingWithFixture(scheduled));

        assertEquals(BookingStatus.CANCELLED,
                bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId()).getStatus());
    }

    @Test
    void configuredCancellationWindowAllowsBeforeCutoffAndRejectsAfterCutoff() {
        BookingManagementService twoHourWindow = bookingServiceWithPolicy(1, Duration.ofHours(2));
        replaceFullWeekOperatingHours(ensureDefaultBranch(), LocalTime.of(8, 0), LocalTime.of(23, 0));
        LocalDateTime now = branchLocalNow();
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
                newBookingWithFixture(branchLocalNow().plusHours(2)));

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
        return bookingServiceWithRepositories(
                bookingRepository, notificationService,
                new BookingPolicyProperties(capacity, cancellationWindow));
    }

    private LocalDateTime branchLocalNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Africa/Johannesburg"));
    }

    private Booking newBookingForScope(Booking scope, LocalDateTime scheduledDateTime) {
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        return new Booking(
                ids.booking(), user, vehicle, scope.getBranchId(), scope.getServiceOfferingId(),
                scope.getService(), scheduledDateTime, "same offering capacity");
    }

    private BookingManagementService bookingServiceWithRepositories(
            BookingRepository bookings,
            NotificationManagementService notifications,
            BookingPolicyProperties policy
    ) {
        return new BookingManagementService(
                bookings, userRepository, vehicleRepository, catalogService,
                serviceOfferingService, marketplaceService,
                queueRepository, notificationRepository, notifications, queueOrdering, coordinator, policy,
                new BookingSlotPolicyService(bookings, policy, clock),
                new BranchAvailabilityDecisionService(
                        bookings, marketplaceService, branchSchedulingService, serviceOfferingService,
                        catalogService, policy, clock),
                clock);
    }

    private boolean attemptConcurrentReschedule(
            String bookingId,
            LocalDateTime target,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            bookingService.rescheduleBooking(bookingId, target);
            return true;
        } catch (BusinessRuleViolationException exception) {
            assertEquals("Booking time slot is already full", exception.getMessage());
            return false;
        }
    }

    private QueueEntry canonicalQueue(QueueEntry response) {
        return queueRepository.findById(response.getQueueEntryId()).orElseThrow();
    }

    private static final class FailingUpdateBookingRepository implements BookingRepository {

        private final BookingRepository delegate;
        private boolean failNextUpdate;

        private FailingUpdateBookingRepository(BookingRepository delegate) {
            this.delegate = delegate;
        }

        void failNextUpdate() {
            failNextUpdate = true;
        }

        @Override
        public boolean insert(Booking entity) {
            return delegate.insert(entity);
        }

        @Override
        public boolean update(Booking entity) {
            if (failNextUpdate) {
                failNextUpdate = false;
                throw new IllegalStateException("booking update unavailable");
            }
            return delegate.update(entity);
        }

        @Override
        public Optional<Booking> findById(String id) {
            return delegate.findById(id);
        }

        @Override
        public List<Booking> findAll() {
            return delegate.findAll();
        }

        @Override
        public boolean deleteById(String id) {
            return delegate.deleteById(id);
        }

        @Override
        public boolean existsById(String id) {
            return delegate.existsById(id);
        }

        @Override
        public List<Booking> findByUserId(String userId) {
            return delegate.findByUserId(userId);
        }

        @Override
        public List<Booking> findByVehicleId(String vehicleId) {
            return delegate.findByVehicleId(vehicleId);
        }

        @Override
        public List<Booking> findByServiceId(String serviceId) {
            return delegate.findByServiceId(serviceId);
        }

        @Override
        public List<Booking> findByBranchId(String branchId) {
            return delegate.findByBranchId(branchId);
        }

        @Override
        public List<Booking> findByServiceOfferingId(String serviceOfferingId) {
            return delegate.findByServiceOfferingId(serviceOfferingId);
        }

        @Override
        public List<Booking> findByScheduledDateTime(LocalDateTime scheduledDateTime) {
            return delegate.findByScheduledDateTime(scheduledDateTime);
        }

        @Override
        public boolean existsByUserId(String userId) {
            return delegate.existsByUserId(userId);
        }

        @Override
        public boolean existsByVehicleId(String vehicleId) {
            return delegate.existsByVehicleId(vehicleId);
        }

        @Override
        public boolean existsByServiceId(String serviceId) {
            return delegate.existsByServiceId(serviceId);
        }

        @Override
        public boolean existsByServiceOfferingId(String serviceOfferingId) {
            return delegate.existsByServiceOfferingId(serviceOfferingId);
        }
    }
}
