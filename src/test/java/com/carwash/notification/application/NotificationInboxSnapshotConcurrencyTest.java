package com.carwash.notification.application;

import com.carwash.access.application.TenantAccessContext;
import com.carwash.identity.domain.RoleName;
import com.carwash.identity.domain.User;
import com.carwash.notification.domain.DeliveryStatus;
import com.carwash.notification.domain.Notification;
import com.carwash.notification.domain.NotificationCursor;
import com.carwash.notification.domain.NotificationInboxSnapshot;
import com.carwash.notification.infrastructure.InMemoryNotificationRepository;
import com.carwash.shared.application.DataTransactionOperations;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationInboxSnapshotConcurrencyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2089-01-15T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void markOneCommittedAfterSnapshotCannotContradictReturnedSentRowAndUnreadCount() throws Exception {
        PausingInboxRepository repository = new PausingInboxRepository();
        Notification notification = unread("notification-one", "customer-one");
        assertTrue(repository.insert(notification));
        NotificationManagementService service = service(repository);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var response = executor.submit(() -> service.findInbox(
                    new TenantAccessContext("customer-one", RoleName.CUSTOMER, null),
                    "customer-one", null, false, null, 10));

            repository.awaitSnapshot();
            try {
                repository.markAsReadByUserId(
                        "notification-one", "customer-one", LocalDateTime.now(CLOCK).plusMinutes(1));
            } finally {
                repository.releaseResponse();
            }

            NotificationInboxPage page = response.get(10, TimeUnit.SECONDS);
            assertEquals(DeliveryStatus.SENT, page.notifications().getFirst().deliveryStatus());
            assertEquals(1, page.unreadCount());
            assertEquals(0, repository.countUnreadByUserId("customer-one"));
        }
    }

    @Test
    void markAllCommittedAfterSnapshotCannotContradictReturnedRowsAndUnreadCount() throws Exception {
        PausingInboxRepository repository = new PausingInboxRepository();
        assertTrue(repository.insert(unread("notification-one", "customer-one")));
        assertTrue(repository.insert(unread("notification-two", "customer-one")));
        NotificationManagementService service = service(repository);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var response = executor.submit(() -> service.findInbox(
                    new TenantAccessContext("customer-one", RoleName.CUSTOMER, null),
                    "customer-one", null, true, null, 10));

            repository.awaitSnapshot();
            try {
                assertEquals(2, repository.markAllAsReadByUserId(
                        "customer-one", LocalDateTime.now(CLOCK).plusMinutes(1)));
            } finally {
                repository.releaseResponse();
            }

            NotificationInboxPage page = response.get(10, TimeUnit.SECONDS);
            assertEquals(2, page.notifications().size());
            assertTrue(page.notifications().stream()
                    .allMatch(notification -> notification.deliveryStatus() == DeliveryStatus.SENT));
            assertEquals(2, page.unreadCount());
            assertEquals(0, repository.countUnreadByUserId("customer-one"));
        }
    }

    private static NotificationManagementService service(InMemoryNotificationRepository repository) {
        return new NotificationManagementService(repository, null, null, new DirectTransactions(),
                () -> "unused", new NotificationPolicyProperties(10), CLOCK);
    }

    private static Notification unread(String notificationId, String userId) {
        User user = new User();
        user.setUserId(userId);
        Notification notification = new Notification(
                notificationId, user, null, "TEST", "message", "IN_APP");
        notification.send(LocalDateTime.now(CLOCK));
        return notification;
    }

    private static final class PausingInboxRepository extends InMemoryNotificationRepository {
        private final CountDownLatch snapshotCaptured = new CountDownLatch(1);
        private final CountDownLatch responseReleased = new CountDownLatch(1);

        @Override
        public NotificationInboxSnapshot findInboxByUserId(
                String userId, boolean unreadOnly, NotificationCursor cursor, int limit) {
            NotificationInboxSnapshot snapshot = super.findInboxByUserId(userId, unreadOnly, cursor, limit);
            snapshotCaptured.countDown();
            if (!await(responseReleased)) {
                throw new IllegalStateException("Timed out waiting to release the inbox response");
            }
            return snapshot;
        }

        void awaitSnapshot() {
            assertTrue(await(snapshotCaptured), "Inbox snapshot was not captured");
        }

        void releaseResponse() {
            responseReleased.countDown();
        }

        private static boolean await(CountDownLatch latch) {
            try {
                return latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
    }

    private static final class DirectTransactions implements DataTransactionOperations {
        @Override public <T> T read(Supplier<T> action) { return action.get(); }
        @Override public <T> T write(Supplier<T> action) { return action.get(); }
        @Override public void compensate(RuntimeException failure, Runnable compensation) { compensation.run(); }
        @Override public void afterCommitBestEffort(
                Runnable action, Consumer<RuntimeException> failureHandler) { action.run(); }
    }
}
