package com.carwash.service;

import com.carwash.config.BookingPolicyProperties;
import com.carwash.config.NotificationPolicyProperties;
import com.carwash.domain.Booking;
import com.carwash.domain.Notification;
import com.carwash.domain.QueueEntry;
import com.carwash.domain.User;
import com.carwash.enums.BookingStatus;
import com.carwash.enums.QueueStatus;
import com.carwash.service.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationManagementServiceTest extends ServiceTestSupport {

    @Test
    void bookingConfirmationCreatesNotification() {
        Booking booking = createSavedBooking();
        bookingService.confirmBooking(booking.getBookingId());
        List<Notification> notifications = notificationService.findByUserId(booking.getUser().getUserId());
        assertEquals(1, notifications.size());
        assertEquals("BOOKING_CONFIRMED", notifications.getFirst().getType());
        assertEquals("Your booking has been confirmed.", notifications.getFirst().getMessage());
    }

    @Test
    void bookingCancellationCreatesNotification() {
        Booking booking = createSavedBooking();
        bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId());
        List<Notification> notifications = notificationService.findByUserId(booking.getUser().getUserId());
        assertEquals(1, notifications.size());
        assertEquals("BOOKING_CANCELLED", notifications.getFirst().getType());
        assertEquals("Your booking has been cancelled.", notifications.getFirst().getMessage());
    }

    @Test
    void queueCallCreatesNotification() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callNext(queueEntry.getQueueEntryId());
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
        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());
        List<Notification> notifications = notificationService.findByUserId(queueEntry.getBooking().getUser().getUserId());
        assertEquals(List.of("BOOKING_CONFIRMED", "QUEUE_CALLED", "SERVICE_STARTED"),
                notifications.stream().map(Notification::getType).toList());
        assertEquals("Your service has started.", notifications.get(2).getMessage());
        assertEquals(BookingStatus.IN_SERVICE, notifications.get(2).getBooking().getStatus());
    }

    @Test
    void queueServiceCompletionCreatesNotification() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());
        queueService.completeQueueEntry(queueEntry.getQueueEntryId());
        List<Notification> notifications = notificationService.findByUserId(queueEntry.getBooking().getUser().getUserId());
        assertEquals(List.of("BOOKING_CONFIRMED", "QUEUE_CALLED", "SERVICE_STARTED", "SERVICE_COMPLETED"),
                notifications.stream().map(Notification::getType).toList());
        assertEquals("Your service has been completed.", notifications.get(3).getMessage());
        assertEquals(BookingStatus.COMPLETED, notifications.get(3).getBooking().getStatus());
    }

    @Test
    void cancellationRemainsSuccessfulWhenLifecycleNotificationFails() {
        Booking booking = createConfirmedBooking();
        QueueEntry queueEntry = queueService.createQueueEntry(
                new QueueEntry(ids.queueEntry(), booking, booking.getService()));
        BookingManagementService serviceWithFailingNotifications = bookingServiceWith(failingNotificationService());

        Booking cancelled = assertDoesNotThrow(() -> serviceWithFailingNotifications.cancelBooking(
                booking.getBookingId(), booking.getUser().getUserId()));

        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertTrue(queueRepository.findById(queueEntry.getQueueEntryId()).isEmpty());
        assertFalse(notificationTypes(booking).contains("BOOKING_CANCELLED"));
    }

    @Test
    void serviceStartRemainsSuccessfulWhenLifecycleNotificationFails() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callNext(queueEntry.getQueueEntryId());
        QueueManagementService serviceWithFailingNotifications = queueServiceWith(failingNotificationService());

        QueueEntry started = assertDoesNotThrow(
                () -> serviceWithFailingNotifications.startService(queueEntry.getQueueEntryId()));

        assertEquals(QueueStatus.IN_PROGRESS, started.getQueueStatus());
        assertEquals(BookingStatus.IN_SERVICE, started.getBooking().getStatus());
        assertFalse(notificationTypes(queueEntry.getBooking()).contains("SERVICE_STARTED"));
    }

    @Test
    void serviceCompletionRemainsSuccessfulWhenLifecycleNotificationFails() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());
        QueueManagementService serviceWithFailingNotifications = queueServiceWith(failingNotificationService());

        QueueEntry completed = assertDoesNotThrow(
                () -> serviceWithFailingNotifications.completeQueueEntry(queueEntry.getQueueEntryId()));

        assertEquals(QueueStatus.COMPLETED, completed.getQueueStatus());
        assertEquals(BookingStatus.COMPLETED, completed.getBooking().getStatus());
        assertFalse(notificationTypes(queueEntry.getBooking()).contains("SERVICE_COMPLETED"));
    }

    @Test
    void recentNotificationsCanBeRetrievedByUserId() {
        Booking booking = createSavedBooking();
        bookingService.confirmBooking(booking.getBookingId());
        bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId());
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
        bookingService.confirmBooking(booking.getBookingId());
        Notification notification = notificationService.findByUserId(booking.getUser().getUserId()).getFirst();
        assertEquals("notification-00000000000000000001", notification.getNotificationId());
    }

    private NotificationManagementService failingNotificationService() {
        return new NotificationManagementService(
                notificationRepository, userRepository, bookingRepository, coordinator, notificationIds,
                new NotificationPolicyProperties(10), clock) {
            @Override
            public Notification createNotification(User user, Booking booking, String type, String message) {
                throw new ResourceNotFoundException("Injected notification persistence failure");
            }
        };
    }

    private BookingManagementService bookingServiceWith(NotificationManagementService notifications) {
        return new BookingManagementService(
                bookingRepository, userRepository, vehicleRepository, serviceRepository,
                queueRepository, notificationRepository, notifications, queueOrdering, coordinator,
                new BookingPolicyProperties(1, Duration.ZERO), clock);
    }

    private QueueManagementService queueServiceWith(NotificationManagementService notifications) {
        return new QueueManagementService(
                queueRepository, bookingRepository, serviceRepository, notifications, coordinator,
                queueOrdering, clock);
    }

    private List<String> notificationTypes(Booking booking) {
        return notificationService.findByUserId(booking.getUser().getUserId()).stream()
                .map(Notification::getType)
                .toList();
    }
}
