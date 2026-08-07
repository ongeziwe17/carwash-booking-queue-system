package com.carwash.repository.inmemory;

import com.carwash.domain.Booking;
import com.carwash.domain.QueueEntry;
import com.carwash.domain.Role;
import com.carwash.domain.Service;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRepositoryCrudTest {

    @Test
    void userRepositoryCrudAndFindByEmail() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        User user = user("u-1", "alice@example.com");

        repository.save(user);
        repository.printStorage();

        assertTrue(repository.findById("u-1").isPresent());
        assertTrue(repository.findByEmail("ALICE@example.com").isPresent());
        assertTrue(repository.findById("unknown").isEmpty());
        assertEquals(1, repository.findAll().size());

        User updated = user("u-1", "alice-updated@example.com");
        repository.save(updated);
        repository.printStorage();
        assertEquals("alice-updated@example.com", repository.findById("u-1").orElseThrow().getEmail());

        repository.delete("u-1");
        assertTrue(repository.findById("u-1").isEmpty());
    }

    @Test
    void serviceRepositoryCrud() {
        InMemoryServiceRepository repository = new InMemoryServiceRepository();
        Service service = service("s-1", "Exterior Wash");

        repository.save(service);
        repository.printStorage();

        assertTrue(repository.findById("s-1").isPresent());
        assertTrue(repository.findById("missing").isEmpty());
        assertEquals(1, repository.findAll().size());

        Service updated = service("s-1", "Premium Exterior Wash");
        repository.save(updated);
        repository.printStorage();
        assertEquals("Premium Exterior Wash", repository.findById("s-1").orElseThrow().getServiceName());

        repository.delete("s-1");
        assertTrue(repository.findById("s-1").isEmpty());
    }

    @Test
    void bookingRepositoryCrudAndFindByUserId() {
        InMemoryBookingRepository repository = new InMemoryBookingRepository();
        User user = user("u-2", "bob@example.com");
        Booking booking = booking("b-1", user, service("s-2", "Deluxe"));

        repository.save(booking);
        repository.printStorage();

        assertTrue(repository.findById("b-1").isPresent());
        assertEquals(1, repository.findByUserId("u-2").size());
        assertTrue(repository.findById("missing").isEmpty());
        assertEquals(1, repository.findAll().size());

        Booking updated = booking("b-1", user, service("s-3", "Full Detail"));
        repository.save(updated);
        assertEquals("s-3", repository.findById("b-1").orElseThrow().getService().getServiceId());

        repository.delete("b-1");
        assertTrue(repository.findById("b-1").isEmpty());
    }

    @Test
    void queueEntryRepositoryCrudAndFindByServiceId() {
        InMemoryQueueEntryRepository repository = new InMemoryQueueEntryRepository();
        Service service = service("s-10", "Quick Wash");
        QueueEntry queueEntry = queueEntry("q-1", service);

        repository.save(queueEntry);
        repository.printStorage();

        assertTrue(repository.findById("q-1").isPresent());
        assertEquals(1, repository.findByServiceId("s-10").size());
        assertTrue(repository.findById("missing").isEmpty());
        assertEquals(1, repository.findAll().size());

        QueueEntry updated = queueEntry("q-1", service("s-11", "Polish"));
        repository.save(updated);
        assertEquals("s-11", repository.findById("q-1").orElseThrow().getService().getServiceId());

        repository.delete("q-1");
        assertTrue(repository.findById("q-1").isEmpty());
    }



    @Test
    void storageCanBeInspectedForDebugging() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        repository.save(user("u-debug", "debug@example.com"));

        repository.printStorage();

        assertEquals(1, repository.storageSnapshot().size());
        assertTrue(repository.storageSnapshot().containsKey("u-debug"));
        assertTrue(repository.storageAsString().contains("u-debug"));

        assertThrows(UnsupportedOperationException.class, () ->
                repository.storageSnapshot().put("x", user("x", "x@example.com")));
    }

    private static User user(String id, String email) {
        return User.withEncodedPassword(id, "Test User", email, "01234", "hash", new Role("r-1", "CUSTOMER", "desc", null));
    }

    private static Service service(String id, String name) {
        return new Service(id, name, "desc", BigDecimal.TEN, 30);
    }

    private static Booking booking(String id, User user, Service service) {
        Vehicle vehicle = new Vehicle("v-1", "CA 123", "SEDAN", "Toyota", "Corolla", "White", "");
        return new Booking(id, user, vehicle, service, LocalDateTime.now().plusDays(1), "none");
    }

    private static QueueEntry queueEntry(String id, Service service) {
        Booking booking = booking("b-queue", user("u-queue", "queue@example.com"), service);
        return new QueueEntry(id, booking, service, 1);
    }
}
