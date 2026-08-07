package com.carwash.service;

import com.carwash.domain.Booking;
import com.carwash.domain.Notification;
import com.carwash.domain.QueueEntry;
import org.junit.jupiter.api.Test;

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
        assertEquals(1, notifications.size());
        assertEquals("QUEUE_CALLED", notifications.getFirst().getType());
        assertEquals("Your vehicle is next in the queue.", notifications.getFirst().getMessage());
    }

    @Test
    void queueServiceStartCreatesNotification() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());
        List<Notification> notifications = notificationService.findByUserId(queueEntry.getBooking().getUser().getUserId());
        assertEquals(2, notifications.size());
        assertTrue(notifications.stream().anyMatch(notification -> notification.getType().equals("SERVICE_STARTED")
                && notification.getMessage().equals("Your service has started.")));
    }

    @Test
    void queueServiceCompletionCreatesNotification() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());
        queueService.completeQueueEntry(queueEntry.getQueueEntryId());
        List<Notification> notifications = notificationService.findByUserId(queueEntry.getBooking().getUser().getUserId());
        assertEquals(3, notifications.size());
        assertTrue(notifications.stream().anyMatch(notification -> notification.getType().equals("SERVICE_COMPLETED")
                && notification.getMessage().equals("Your service has been completed.")));
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
    void deterministicNotificationIdsStartFromKnownStateForEveryTest() {
        Booking booking = createSavedBooking();
        bookingService.confirmBooking(booking.getBookingId());
        Notification notification = notificationService.findByUserId(booking.getUser().getUserId()).getFirst();
        assertEquals("notification-00000000000000000001", notification.getNotificationId());
    }
}
