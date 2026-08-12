package com.carwash.repository.inmemory;

import com.carwash.booking.infrastructure.InMemoryBookingRepository;
import com.carwash.catalog.infrastructure.InMemoryServiceRepository;
import com.carwash.identity.infrastructure.InMemoryUserRepository;
import com.carwash.queue.infrastructure.InMemoryQueueEntryRepository;

import com.carwash.booking.domain.Booking;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.identity.domain.Role;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRepositoryCrudTest {

    @Test
    void userRepositoryUsesExplicitInsertUpdateAndDeleteSemantics() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        User user = user("u-1", "alice@example.com");
        assertTrue(repository.insert(user));
        assertTrue(repository.findById("u-1").isPresent());
        assertTrue(repository.findByEmail("ALICE@example.com").isPresent());
        assertFalse(repository.insert(user("u-1", "replacement@example.com")));
        User updated = user("u-1", "alice-updated@example.com");
        assertTrue(repository.update(updated));
        assertEquals("alice-updated@example.com", repository.findById("u-1").orElseThrow().getEmail());
        assertTrue(repository.deleteById("u-1"));
        assertTrue(repository.findById("u-1").isEmpty());
        assertFalse(repository.deleteById("u-1"));
    }

    @Test
    void serviceRepositoryDoesNotUpsert() {
        InMemoryServiceRepository repository = new InMemoryServiceRepository();
        Service service = service("s-1", "Exterior Wash");
        assertTrue(repository.insert(service));
        assertFalse(repository.insert(service("s-1", "Replacement")));
        assertFalse(repository.update(service("missing", "Missing")));
        Service updated = service("s-1", "Premium Exterior Wash");
        assertTrue(repository.update(updated));
        assertEquals("Premium Exterior Wash", repository.findById("s-1").orElseThrow().getServiceName());
    }

    @Test
    void bookingAndQueueQueriesReturnCanonicalRecords() {
        InMemoryBookingRepository bookings = new InMemoryBookingRepository();
        InMemoryQueueEntryRepository queues = new InMemoryQueueEntryRepository();
        User user = user("u-2", "bob@example.com");
        Service service = service("s-2", "Deluxe");
        Booking booking = booking("b-1", user, service);
        QueueEntry queueEntry = new QueueEntry("q-1", booking, service, 1);
        assertTrue(bookings.insert(booking));
        assertTrue(queues.insert(queueEntry));
        assertEquals(1, bookings.findByUserId("u-2").size());
        assertEquals(1, bookings.findByVehicleId("v-1").size());
        assertEquals(1, bookings.findByServiceId("s-2").size());
        assertTrue(bookings.existsByUserId("u-2"));
        assertTrue(bookings.existsByVehicleId("v-1"));
        assertTrue(bookings.existsByServiceId("s-2"));
        assertEquals(1, queues.findByBookingId("b-1").size());
        assertEquals(1, queues.findByServiceId("s-2").size());
        assertTrue(queues.existsByBookingId("b-1"));
        assertTrue(queues.existsActiveByBookingId("b-1"));
        assertTrue(queues.existsByServiceId("s-2"));
    }

    @ParameterizedTest
    @EnumSource(QueueStatus.class)
    void activeQueueLookupUsesCentralizedQueueStatusSemantics(QueueStatus status) {
        InMemoryQueueEntryRepository queues = new InMemoryQueueEntryRepository();
        User user = user("u-active", "active@example.com");
        Service service = service("s-active", "Active lookup");
        Booking booking = booking("b-active", user, service);
        QueueEntry queueEntry = new QueueEntry("q-active", booking, service, 1);
        queueEntry.setQueueStatus(status);
        assertTrue(queues.insert(queueEntry));

        assertEquals(status.isActive(), queues.existsActiveByBookingId("b-active"));
        assertFalse(queues.existsActiveByBookingId("missing"));
        assertFalse(queues.existsActiveByBookingId(null));
    }

    @Test
    void activeQueueQueryOrdersByPositionAndExcludesTerminalEntries() {
        InMemoryQueueEntryRepository queues = new InMemoryQueueEntryRepository();
        User user = user("u-order", "order@example.com");
        Service service = service("s-order", "Ordered queue");
        QueueEntry third = queue("q-third", booking("b-third", user, service), service, 3,
                QueueStatus.WAITING, LocalDateTime.of(2090, 1, 1, 10, 3));
        QueueEntry first = queue("q-first", booking("b-first", user, service), service, 1,
                QueueStatus.IN_PROGRESS, LocalDateTime.of(2090, 1, 1, 10, 1));
        QueueEntry second = queue("q-second", booking("b-second", user, service), service, 2,
                QueueStatus.CALLED, LocalDateTime.of(2090, 1, 1, 10, 2));
        QueueEntry completed = queue("q-completed", booking("b-completed", user, service), service, 1,
                QueueStatus.COMPLETED, LocalDateTime.of(2090, 1, 1, 9, 0));
        QueueEntry exited = queue("q-exited", booking("b-exited", user, service), service, 2,
                QueueStatus.EXITED, LocalDateTime.of(2090, 1, 1, 9, 1));
        List.of(third, completed, first, exited, second).forEach(entry -> assertTrue(queues.insert(entry)));

        assertEquals(List.of(first, second, third), queues.findActiveOrdered());
        assertEquals(List.of(first, second, third), queues.findByServiceId(service.getServiceId()).subList(0, 3));
        assertEquals(List.of(first, second, third, completed, exited), queues.findAllOrdered());
        assertThrows(UnsupportedOperationException.class, () -> queues.findActiveOrdered().add(completed));
    }

    @Test
    void activeQueueQueryBreaksPositionTiesByJoinedTimeThenId() {
        InMemoryQueueEntryRepository queues = new InMemoryQueueEntryRepository();
        User user = user("u-tie", "tie@example.com");
        Service service = service("s-tie", "Tie breaker");
        LocalDateTime joined = LocalDateTime.of(2090, 2, 1, 8, 0);
        QueueEntry late = queue("q-late", booking("b-late", user, service), service, 2,
                QueueStatus.WAITING, joined.plusMinutes(1));
        QueueEntry idB = queue("q-b", booking("b-b", user, service), service, 2,
                QueueStatus.WAITING, joined);
        QueueEntry idA = queue("q-a", booking("b-a", user, service), service, 2,
                QueueStatus.WAITING, joined);
        QueueEntry missingJoinedAt = queue("q-null", booking("b-null", user, service), service, 2,
                QueueStatus.WAITING, null);
        List.of(late, idB, idA, missingJoinedAt).forEach(entry -> assertTrue(queues.insert(entry)));

        assertEquals(List.of(idA, idB, late, missingJoinedAt), queues.findActiveOrdered());
    }

    @Test
    void nextWaitingSelectsLowestPositionRegardlessOfInsertionOrder() {
        InMemoryQueueEntryRepository queues = new InMemoryQueueEntryRepository();
        User user = user("u-next-order", "next-order@example.com");
        Service service = service("s-next-order", "Next order");
        QueueEntry third = queue("q-third", booking("b-third", user, service), service, 3,
                QueueStatus.WAITING, LocalDateTime.of(2090, 4, 1, 8, 3));
        QueueEntry first = queue("q-first", booking("b-first", user, service), service, 1,
                QueueStatus.WAITING, LocalDateTime.of(2090, 4, 1, 8, 1));
        QueueEntry second = queue("q-second", booking("b-second", user, service), service, 2,
                QueueStatus.WAITING, LocalDateTime.of(2090, 4, 1, 8, 2));
        List.of(third, first, second).forEach(entry -> assertTrue(queues.insert(entry)));

        assertEquals(first, queues.findNextWaiting().orElseThrow());
    }

    @Test
    void nextWaitingSkipsCalledAndInProgressEntries() {
        InMemoryQueueEntryRepository queues = new InMemoryQueueEntryRepository();
        User user = user("u-next-skip", "next-skip@example.com");
        Service service = service("s-next-skip", "Next skip");
        QueueEntry called = queue("q-called", booking("b-called", user, service), service, 1,
                QueueStatus.CALLED, LocalDateTime.of(2090, 4, 2, 8, 1));
        QueueEntry inProgress = queue("q-progress", booking("b-progress", user, service), service, 2,
                QueueStatus.IN_PROGRESS, LocalDateTime.of(2090, 4, 2, 8, 2));
        QueueEntry waiting = queue("q-waiting", booking("b-waiting", user, service), service, 3,
                QueueStatus.WAITING, LocalDateTime.of(2090, 4, 2, 8, 3));
        List.of(waiting, called, inProgress).forEach(entry -> assertTrue(queues.insert(entry)));

        assertEquals(waiting, queues.findNextWaiting().orElseThrow());
    }

    @Test
    void nextWaitingIgnoresTerminalEntries() {
        InMemoryQueueEntryRepository queues = new InMemoryQueueEntryRepository();
        User user = user("u-next-terminal", "next-terminal@example.com");
        Service service = service("s-next-terminal", "Next terminal");
        QueueEntry completed = queue("q-completed", booking("b-completed", user, service), service, 1,
                QueueStatus.COMPLETED, LocalDateTime.of(2090, 4, 3, 8, 1));
        QueueEntry exited = queue("q-exited", booking("b-exited", user, service), service, 2,
                QueueStatus.EXITED, LocalDateTime.of(2090, 4, 3, 8, 2));
        List.of(completed, exited).forEach(entry -> assertTrue(queues.insert(entry)));

        assertTrue(queues.findNextWaiting().isEmpty());
    }

    @Test
    void nextWaitingPreservesJoinedTimeThenIdTieBreakers() {
        InMemoryQueueEntryRepository queues = new InMemoryQueueEntryRepository();
        User user = user("u-next-tie", "next-tie@example.com");
        Service service = service("s-next-tie", "Next tie");
        LocalDateTime joined = LocalDateTime.of(2090, 4, 4, 8, 0);
        QueueEntry late = queue("q-0", booking("b-late", user, service), service, 1,
                QueueStatus.WAITING, joined.plusMinutes(1));
        QueueEntry idB = queue("q-b", booking("b-b", user, service), service, 1,
                QueueStatus.WAITING, joined);
        QueueEntry idA = queue("q-a", booking("b-a", user, service), service, 1,
                QueueStatus.WAITING, joined);
        List.of(late, idB, idA).forEach(entry -> assertTrue(queues.insert(entry)));

        assertEquals(idA, queues.findNextWaiting().orElseThrow());
    }

    @Test
    void serviceFilteredQueuePreservesGlobalPositionsAndOperationalOrder() {
        InMemoryQueueEntryRepository queues = new InMemoryQueueEntryRepository();
        User user = user("u-filter", "filter@example.com");
        Service exterior = service("s-exterior", "Exterior");
        Service interior = service("s-interior", "Interior");
        QueueEntry firstExterior = queue("q-exterior-1", booking("b-exterior-1", user, exterior), exterior, 1,
                QueueStatus.WAITING, LocalDateTime.of(2090, 3, 1, 8, 0));
        QueueEntry interiorEntry = queue("q-interior", booking("b-interior", user, interior), interior, 2,
                QueueStatus.WAITING, LocalDateTime.of(2090, 3, 1, 8, 1));
        QueueEntry secondExterior = queue("q-exterior-2", booking("b-exterior-2", user, exterior), exterior, 3,
                QueueStatus.WAITING, LocalDateTime.of(2090, 3, 1, 8, 2));
        List.of(secondExterior, interiorEntry, firstExterior).forEach(entry -> assertTrue(queues.insert(entry)));

        List<QueueEntry> exteriorEntries = queues.findByServiceId(exterior.getServiceId());

        assertEquals(List.of(firstExterior, secondExterior), exteriorEntries);
        assertEquals(List.of(1, 3), exteriorEntries.stream().map(QueueEntry::getPosition).toList());
    }

    @Test
    void storageSnapshotIsSortedAndUnmodifiable() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        repository.insert(user("u-3", "three@example.com"));
        repository.insert(user("u-1", "one@example.com"));
        repository.insert(user("u-2", "two@example.com"));
        assertEquals(java.util.List.of("u-1", "u-2", "u-3"),
                repository.storageSnapshot().keySet().stream().toList());
        assertThrows(UnsupportedOperationException.class,
                () -> repository.storageSnapshot().put("x", user("x", "x@example.com")));
    }

    private static User user(String id, String email) {
        return User.withEncodedPassword(id, "Test User", email, "01234", "hash",
                new Role("r-1", "CUSTOMER", "desc", null));
    }

    private static Service service(String id, String name) {
        return new Service(id, name, "desc", BigDecimal.TEN, 30);
    }

    private static Booking booking(String id, User user, Service service) {
        Vehicle vehicle = new Vehicle("v-1", "CA 123", "SEDAN", "Toyota", "Corolla", "White", "");
        vehicle.setUserId(user.getUserId());
        return new Booking(id, user, vehicle, service, TestDates.future(), "none");
    }

    private static QueueEntry queue(String id, Booking booking, Service service, int position,
                                    QueueStatus status, LocalDateTime joinedAt) {
        QueueEntry queueEntry = new QueueEntry(id, booking, service, position);
        queueEntry.setQueueStatus(status);
        queueEntry.setJoinedAt(joinedAt);
        return queueEntry;
    }
}
