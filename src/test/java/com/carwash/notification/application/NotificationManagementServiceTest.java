package com.carwash.notification.application;

import com.carwash.testsupport.ServiceTestSupport;
import com.carwash.testsupport.TestAccess;

import com.carwash.booking.application.BookingManagementService;
import com.carwash.notification.application.NotificationManagementService;
import com.carwash.queue.application.QueueManagementService;

import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.notification.application.NotificationPolicyProperties;
import com.carwash.booking.domain.Booking;
import com.carwash.notification.domain.Notification;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.identity.domain.User;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationManagementServiceTest extends ServiceTestSupport {

    @Test
    void bookingConfirmationCreatesNotification() {
        Booking booking = createSavedBooking();
        bookingService.confirmBooking(TestAccess.platformAdministrator(), booking.getBookingId());
        List<Notification> notifications = notificationService.findByUserId(booking.getUser().getUserId());
        assertEquals(1, notifications.size());
        assertEquals("BOOKING_CONFIRMED", notifications.getFirst().getType());
        assertEquals("Your booking has been confirmed.", notifications.getFirst().getMessage());
    }

    @Test
    void bookingCancellationCreatesNotification() {
        Booking booking = createSavedBooking();
        bookingService.cancelBooking(TestAccess.platformAdministrator(), booking.getBookingId());
        List<Notification> notifications = notificationService.findByUserId(booking.getUser().getUserId());
        assertEquals(1, notifications.size());
        assertEquals("BOOKING_CANCELLED", notifications.getFirst().getType());
        assertEquals("Your booking has been cancelled.", notifications.getFirst().getMessage());
    }

    @Test
    void queueCallCreatesNotification() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callQueueEntry(TestAccess.platformAdministrator(), queueEntry.getQueueEntryId());
        List<Notification> notifications = notificationService.findByUserId(queueEntry.getBooking().getUser().getUserId());
        assertEquals(List.of("BOOKING_CONFIRMED", "QUEUE_CALLED"),
                notifications.stream().map(Notification::getType).toList());
        Notification queueCalled = notifications.get(1);
        assertEquals("Your vehicle is next in the queue.", queueCalled.getMessage());
        assertEquals(queueEntry.getCalledAt(), queueCalled.getSentAt());
    }

    @Test
    void queueServiceStartCreatesNotification() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callQueueEntry(TestAccess.platformAdministrator(), queueEntry.getQueueEntryId());
        queueService.startService(TestAccess.platformAdministrator(), queueEntry.getQueueEntryId());
        List<Notification> notifications = notificationService.findByUserId(queueEntry.getBooking().getUser().getUserId());
        assertEquals(List.of("BOOKING_CONFIRMED", "QUEUE_CALLED", "SERVICE_STARTED"),
                notifications.stream().map(Notification::getType).toList());
        assertEquals("Your service has started.", notifications.get(2).getMessage());
        assertEquals(BookingStatus.IN_SERVICE, notifications.get(2).getBooking().getStatus());
    }

    @Test
    void queueServiceCompletionCreatesNotification() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callQueueEntry(TestAccess.platformAdministrator(), queueEntry.getQueueEntryId());
        queueService.startService(TestAccess.platformAdministrator(), queueEntry.getQueueEntryId());
        queueService.completeQueueEntry(TestAccess.platformAdministrator(), queueEntry.getQueueEntryId());
        List<Notification> notifications = notificationService.findByUserId(queueEntry.getBooking().getUser().getUserId());
        assertEquals(List.of("BOOKING_CONFIRMED", "QUEUE_CALLED", "SERVICE_STARTED", "SERVICE_COMPLETED"),
                notifications.stream().map(Notification::getType).toList());
        assertEquals("Your service has been completed.", notifications.get(3).getMessage());
        assertEquals(BookingStatus.COMPLETED, notifications.get(3).getBooking().getStatus());
    }

    @Test
    void cancellationRemainsSuccessfulWhenLifecycleNotificationFails() {
        Booking booking = createConfirmedBooking();
        QueueEntry queueEntry = queueService.createQueueEntry(TestAccess.platformAdministrator(),
                ids.queueEntry(), booking.getBookingId(), booking.getService().getServiceId());
        BookingManagementService serviceWithFailingNotifications = bookingServiceWith(failingNotificationService());

        Booking cancelled = assertDoesNotThrow(() -> serviceWithFailingNotifications.cancelBooking(
                TestAccess.platformAdministrator(), booking.getBookingId()));

        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertTrue(queueRepository.findById(queueEntry.getQueueEntryId()).isEmpty());
        assertFalse(notificationTypes(booking).contains("BOOKING_CANCELLED"));
    }

    @Test
    void serviceStartRemainsSuccessfulWhenLifecycleNotificationFails() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callQueueEntry(TestAccess.platformAdministrator(), queueEntry.getQueueEntryId());
        QueueManagementService serviceWithFailingNotifications = queueServiceWith(failingNotificationService());

        QueueEntry started = assertDoesNotThrow(
                () -> serviceWithFailingNotifications.startService(
                        TestAccess.platformAdministrator(), queueEntry.getQueueEntryId()));

        assertEquals(QueueStatus.IN_PROGRESS, started.getQueueStatus());
        assertEquals(BookingStatus.IN_SERVICE, started.getBooking().getStatus());
        assertFalse(notificationTypes(queueEntry.getBooking()).contains("SERVICE_STARTED"));
    }

    @Test
    void serviceCompletionRemainsSuccessfulWhenLifecycleNotificationFails() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callQueueEntry(TestAccess.platformAdministrator(), queueEntry.getQueueEntryId());
        queueService.startService(TestAccess.platformAdministrator(), queueEntry.getQueueEntryId());
        QueueManagementService serviceWithFailingNotifications = queueServiceWith(failingNotificationService());

        QueueEntry completed = assertDoesNotThrow(
                () -> serviceWithFailingNotifications.completeQueueEntry(
                        TestAccess.platformAdministrator(), queueEntry.getQueueEntryId()));

        assertEquals(QueueStatus.COMPLETED, completed.getQueueStatus());
        assertEquals(BookingStatus.COMPLETED, completed.getBooking().getStatus());
        assertFalse(notificationTypes(queueEntry.getBooking()).contains("SERVICE_COMPLETED"));
    }

    @Test
    void recentNotificationsCanBeRetrievedByUserId() {
        Booking booking = createSavedBooking();
        bookingService.confirmBooking(TestAccess.platformAdministrator(), booking.getBookingId());
        bookingService.cancelBooking(TestAccess.platformAdministrator(), booking.getBookingId());
        List<Notification> notifications = notificationService.findRecentByUserId(booking.getUser().getUserId());
        assertEquals(2, notifications.size());
        assertEquals("BOOKING_CANCELLED", notifications.getFirst().getType());
    }

    @Test
    void configuredRecentLimitAppliesOnlyToDefaultLookup() {
        NotificationManagementService limitThree = new NotificationManagementService(
                notificationRepository, userRepository, bookingRepository, coordinator, notificationIds,
                new NotificationPolicyProperties(3), clock);
        Booking booking = createSavedBooking();
        for (int index = 1; index <= 5; index++) {
            limitThree.createNotification(booking.getUser(), booking, "TEST_" + index, "message " + index);
        }

        assertEquals(3, limitThree.findRecentByUserId(booking.getUser().getUserId()).size());
        assertEquals(4, limitThree.findRecentByUserId(booking.getUser().getUserId(), 4).size());
    }

    @Test
    void notificationLifecycleTimestampsUseApplicationClock() {
        Booking booking = createSavedBooking();
        Notification notification = notificationService.createNotification(
                booking.getUser(), booking, "TEST", "clock timestamp");

        assertEquals(LocalDateTime.now(clock), notification.getSentAt());

        notificationService.markAsRead(notification.getNotificationId());
        assertEquals(LocalDateTime.now(clock), notification.getReadAt());
    }

    @Test
    void deterministicNotificationIdsStartFromKnownStateForEveryTest() {
        Booking booking = createSavedBooking();
        bookingService.confirmBooking(TestAccess.platformAdministrator(), booking.getBookingId());
        Notification notification = notificationService.findByUserId(booking.getUser().getUserId()).getFirst();
        assertEquals("notification-00000000000000000001", notification.getNotificationId());
    }

    private NotificationManagementService failingNotificationService() {
        return new NotificationManagementService(
                notificationRepository, userRepository, bookingRepository, coordinator, notificationIds,
                new NotificationPolicyProperties(10), clock) {
            @Override
            public Notification publishForAuthorizedBooking(Booking booking, String type, String message) {
                throw new ResourceNotFoundException("Injected notification persistence failure");
            }
        };
    }

    private BookingManagementService bookingServiceWith(NotificationManagementService notifications) {
        BookingPolicyProperties policy = new BookingPolicyProperties(1, Duration.ZERO);
        return new BookingManagementService(
                bookingRepository, userRepository, vehicleRepository, catalogService,
                serviceOfferingService, marketplaceService,
                queueRepository, notificationRepository, notifications, queueOrdering, coordinator,
                policy, new com.carwash.booking.application.BookingSlotPolicyService(
                        bookingRepository, policy, clock),
                new com.carwash.booking.application.BranchAvailabilityDecisionService(
                        bookingRepository, marketplaceService, branchSchedulingService, serviceOfferingService,
                        catalogService, policy, clock), clock);
    }

    private QueueManagementService queueServiceWith(NotificationManagementService notifications) {
        return new QueueManagementService(
                queueRepository, bookingRepository, serviceOfferingService, marketplaceService,
                notifications, coordinator,
                queueOrdering, clock);
    }

    private List<String> notificationTypes(Booking booking) {
        return notificationService.findByUserId(booking.getUser().getUserId()).stream()
                .map(Notification::getType)
                .toList();
    }
}
