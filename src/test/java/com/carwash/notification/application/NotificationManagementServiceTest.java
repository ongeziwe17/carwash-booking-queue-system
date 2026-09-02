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
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.access.application.TenantAccessContext;
import com.carwash.identity.domain.RoleName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

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

        var read = notificationService.markAsRead(
                TestAccess.customer(booking.getUser().getUserId()), notification.getNotificationId());
        assertEquals(LocalDateTime.now(clock), read.readAt());
    }

    @Test
    void inboxUsesNewestFirstKeysetsWithoutDuplicatesOrOmissionsAtEqualTimes() {
        Booking booking = createSavedBooking();
        for (int index = 1; index <= 5; index++) {
            notificationService.createNotification(
                    booking.getUser(), booking, "PAGE_" + index, "message " + index);
        }
        var access = TestAccess.customer(booking.getUser().getUserId());

        NotificationInboxPage first = notificationService.findInbox(
                access, booking.getUser().getUserId(), null, false, null, 2);
        NotificationInboxPage second = notificationService.findInbox(
                access, booking.getUser().getUserId(), null, false, first.nextCursor(), 2);
        NotificationInboxPage third = notificationService.findInbox(
                access, booking.getUser().getUserId(), null, false, second.nextCursor(), 2);
        NotificationManagementService defaultTwo = new NotificationManagementService(
                notificationRepository, userRepository, bookingRepository, coordinator, notificationIds,
                new NotificationPolicyProperties(10, 2, 4), clock);
        NotificationInboxPage configuredDefault = defaultTwo.findInbox(
                access, booking.getUser().getUserId(), null, false, null, null);

        assertEquals(List.of("PAGE_5", "PAGE_4"), first.notifications().stream().map(value -> value.type()).toList());
        assertEquals(List.of("PAGE_3", "PAGE_2"), second.notifications().stream().map(value -> value.type()).toList());
        assertEquals(List.of("PAGE_1"), third.notifications().stream().map(value -> value.type()).toList());
        assertEquals(5, first.unreadCount());
        assertEquals(2, configuredDefault.notifications().size());
        assertNotNull(configuredDefault.nextCursor());
        assertNull(third.nextCursor());
        assertEquals(5, Set.copyOf(java.util.stream.Stream.of(first, second, third)
                .flatMap(page -> page.notifications().stream())
                .map(value -> value.notificationId()).toList()).size());
    }

    @Test
    void inboxRejectsMalformedOversizedOutOfScopeCursorsAndLimits() {
        Booking booking = createSavedBooking();
        notificationService.createNotification(booking.getUser(), booking, "CURSOR", "cursor");
        notificationService.createNotification(booking.getUser(), booking, "CURSOR_2", "cursor 2");
        var access = TestAccess.customer(booking.getUser().getUserId());
        NotificationInboxPage first = notificationService.findInbox(
                access, booking.getUser().getUserId(), null, false, null, 1);

        assertThrows(BusinessRuleViolationException.class, () -> notificationService.findInbox(
                access, booking.getUser().getUserId(), null, false, "not-base64", 1));
        assertThrows(BusinessRuleViolationException.class, () -> notificationService.findInbox(
                access, booking.getUser().getUserId(), null, false, "x".repeat(513), 1));
        assertThrows(BusinessRuleViolationException.class, () -> notificationService.findInbox(
                access, booking.getUser().getUserId(), null, false, null, 101));
        assertNotNull(first.nextCursor());
        assertThrows(BusinessRuleViolationException.class, () -> notificationService.findInbox(
                access, booking.getUser().getUserId(), null, true, first.nextCursor(), 1));
    }

    @Test
    void unreadFilterAndCountCoverTheCompleteAuthorizedInbox() {
        Booking booking = createSavedBooking();
        Notification first = notificationService.createNotification(
                booking.getUser(), booking, "FIRST", "first");
        notificationService.createNotification(booking.getUser(), booking, "SECOND", "second");
        var access = TestAccess.customer(booking.getUser().getUserId());
        notificationService.markAsRead(access, first.getNotificationId());

        NotificationInboxPage unread = notificationService.findInbox(
                access, booking.getUser().getUserId(), null, true, null, 1);
        NotificationInboxPage all = notificationService.findInbox(
                access, booking.getUser().getUserId(), null, false, null, 1);

        assertEquals(List.of("SECOND"), unread.notifications().stream().map(value -> value.type()).toList());
        assertEquals(1, unread.unreadCount());
        assertEquals(1, all.unreadCount());
        assertEquals(1, all.notifications().size());
    }

    @Test
    void repeatedMarkOneRetainsTheFirstApplicationClockTimestamp() {
        Booking booking = createSavedBooking();
        Notification notification = notificationService.createNotification(
                booking.getUser(), booking, "READ_ONCE", "read once");
        NotificationManagementService advancing = notificationServiceWithClock(new AdvancingClock());
        var access = TestAccess.customer(booking.getUser().getUserId());

        var first = advancing.markAsRead(access, notification.getNotificationId());
        var repeated = advancing.markAsRead(access, notification.getNotificationId());

        assertEquals(first.readAt(), repeated.readAt());
        assertEquals(com.carwash.notification.domain.DeliveryStatus.READ, repeated.deliveryStatus());
    }

    @Test
    void markAllUsesOneTimestampDoesNotRewriteReadRecordsAndRepeatsAsZero() {
        Booking booking = createSavedBooking();
        Notification alreadyRead = notificationService.createNotification(
                booking.getUser(), booking, "EXISTING", "existing");
        Notification unreadOne = notificationService.createNotification(
                booking.getUser(), booking, "UNREAD_ONE", "one");
        Notification unreadTwo = notificationService.createNotification(
                booking.getUser(), booking, "UNREAD_TWO", "two");
        var access = TestAccess.customer(booking.getUser().getUserId());
        var existing = notificationService.markAsRead(access, alreadyRead.getNotificationId());
        NotificationManagementService advancing = notificationServiceWithClock(new AdvancingClock());

        MarkAllNotificationsReadResult result = advancing.markAllAsRead(access, booking.getUser().getUserId());
        MarkAllNotificationsReadResult repeated = advancing.markAllAsRead(access, booking.getUser().getUserId());

        assertEquals(2, result.affectedCount());
        assertEquals(0, repeated.affectedCount());
        assertEquals(result.readAt(), notificationRepository.findSnapshotByIdAndUserId(
                unreadOne.getNotificationId(), booking.getUser().getUserId()).orElseThrow().readAt());
        assertEquals(result.readAt(), notificationRepository.findSnapshotByIdAndUserId(
                unreadTwo.getNotificationId(), booking.getUser().getUserId()).orElseThrow().readAt());
        assertEquals(existing.readAt(), notificationRepository.findSnapshotByIdAndUserId(
                alreadyRead.getNotificationId(), booking.getUser().getUserId()).orElseThrow().readAt());
    }

    @Test
    void concurrentMarkOneCallsReturnOneStableAuthoritativeState() throws Exception {
        Booking booking = createSavedBooking();
        Notification notification = notificationService.createNotification(
                booking.getUser(), booking, "CONCURRENT", "concurrent");
        NotificationManagementService advancing = notificationServiceWithClock(new AdvancingClock());
        var access = TestAccess.customer(booking.getUser().getUserId());
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<com.carwash.notification.domain.NotificationSnapshot> first = executor.submit(() -> {
                start.await();
                return advancing.markAsRead(access, notification.getNotificationId());
            });
            Future<com.carwash.notification.domain.NotificationSnapshot> second = executor.submit(() -> {
                start.await();
                return advancing.markAsRead(access, notification.getNotificationId());
            });
            assertEquals(first.get().readAt(), second.get().readAt());
        }
    }

    @Test
    void readAndMutationAuthorizationRemainSubjectAndTenantScoped() {
        Booking booking = createSavedBooking();
        Notification notification = notificationService.createNotification(
                booking.getUser(), booking, "SCOPED", "scoped");
        String userId = booking.getUser().getUserId();
        TenantAccessContext owner = new TenantAccessContext(
                "owner", RoleName.BUSINESS_OWNER, defaultBusinessId);

        assertEquals(1, notificationService.findInbox(owner, userId, null, false, null, 10)
                .notifications().size());
        assertTrue(notificationService.findInbox(new TenantAccessContext(
                        "foreign-owner", RoleName.BUSINESS_OWNER, "foreign-business"),
                userId, null, false, null, 10).notifications().isEmpty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> notificationService.findInbox(TestAccess.customer("other-user"),
                        userId, null, false, null, 10));
        assertThrows(BusinessRuleViolationException.class,
                () -> notificationService.findInbox(TestAccess.platformAdministrator(),
                        userId, null, false, null, 10));
        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.markAsRead(TestAccess.customer("other-user"),
                        notification.getNotificationId()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> notificationService.markAllAsRead(TestAccess.platformAdministrator(), userId));
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

    private NotificationManagementService notificationServiceWithClock(Clock requestedClock) {
        return new NotificationManagementService(
                notificationRepository, userRepository, bookingRepository, coordinator, notificationIds,
                new NotificationPolicyProperties(10), requestedClock);
    }

    private static final class AdvancingClock extends Clock {
        private final AtomicLong seconds = new AtomicLong();

        @Override public ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() {
            return Instant.parse("2090-01-01T00:00:00Z").plusSeconds(seconds.getAndIncrement());
        }
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
