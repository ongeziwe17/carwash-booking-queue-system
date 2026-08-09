package com.carwash.repository.inmemory;

import com.carwash.domain.Booking;
import com.carwash.domain.QueueEntry;
import com.carwash.domain.Role;
import com.carwash.domain.Service;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.enums.QueueStatus;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

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
}
