package com.carwash.repository;

import com.carwash.domain.Booking;
import com.carwash.domain.Notification;
import com.carwash.domain.QueueEntry;
import com.carwash.domain.Role;
import com.carwash.domain.Service;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.repository.inmemory.InMemoryBookingRepository;
import com.carwash.repository.inmemory.InMemoryNotificationRepository;
import com.carwash.repository.inmemory.InMemoryQueueEntryRepository;
import com.carwash.repository.inmemory.InMemoryServiceRepository;
import com.carwash.repository.inmemory.InMemoryUserRepository;
import com.carwash.repository.inmemory.InMemoryVehicleRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRepositoryIntegrityTest {

    @Test
    void duplicateIdsNeverReplaceExistingRecords() {
        assertDuplicateProtected(new InMemoryUserRepository(), user("u-1", "first@example.com"),
                user("u-1", "replacement@example.com"));
        assertDuplicateProtected(new InMemoryVehicleRepository(), vehicle("v-1", "ABC123"),
                vehicle("v-1", "XYZ999"));
        assertDuplicateProtected(new InMemoryServiceRepository(), service("s-1", "Basic"),
                service("s-1", "Replacement"));
        User user = user("u-booking", "booking@example.com");
        Vehicle vehicle = vehicle("v-booking", "BOOK1");
        vehicle.setUserId(user.getUserId());
        Service service = service("s-booking", "Booking Service");
        Booking firstBooking = booking("b-1", user, vehicle, service);
        Booking replacementBooking = booking("b-1", user, vehicle, service("s-other", "Other"));
        assertDuplicateProtected(new InMemoryBookingRepository(), firstBooking, replacementBooking);
        assertDuplicateProtected(new InMemoryQueueEntryRepository(),
                new QueueEntry("q-1", firstBooking, service, 1),
                new QueueEntry("q-1", firstBooking, service, 2));
        assertDuplicateProtected(new InMemoryNotificationRepository(),
                new Notification("n-1", user, firstBooking, "TYPE", "first", "IN_APP"),
                new Notification("n-1", user, firstBooking, "TYPE", "replacement", "IN_APP"));
    }

    @Test
    void updateNeverCreatesMissingRecord() {
        InMemoryServiceRepository repository = new InMemoryServiceRepository();
        assertFalse(repository.update(service("missing", "Missing")));
        assertTrue(repository.findAll().isEmpty());
        Service existing = service("s-1", "Original");
        assertTrue(repository.insert(existing));
        Service updated = service("s-1", "Updated");
        assertTrue(repository.update(updated));
        assertSame(updated, repository.findById("s-1").orElseThrow());
    }

    @Test
    void concurrentDuplicateInsertHasExactlyOneWinner() throws Exception {
        InMemoryServiceRepository repository = new InMemoryServiceRepository();
        int workerCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int index = 0; index < workerCount; index++) {
                int worker = index;
                results.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return repository.insert(service("same-id", "Service " + worker));
                }));
            }
            start.countDown();
            long successfulInserts = 0;
            for (Future<Boolean> result : results) if (result.get(5, TimeUnit.SECONDS)) successfulInserts++;
            assertEquals(1, successfulInserts);
            assertEquals(1, repository.findAll().size());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentUniqueInsertsLoseNoRecordsAndRemainSorted() throws Exception {
        InMemoryServiceRepository repository = new InMemoryServiceRepository();
        int workerCount = 24;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int index = workerCount - 1; index >= 0; index--) {
                int worker = index;
                results.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return repository.insert(service(String.format("s-%02d", worker), "Service " + worker));
                }));
            }
            start.countDown();
            for (Future<Boolean> result : results) assertTrue(result.get(5, TimeUnit.SECONDS));
            assertEquals(workerCount, repository.findAll().size());
            assertEquals(repository.findAll().stream().map(Service::getServiceId).sorted().toList(),
                    repository.findAll().stream().map(Service::getServiceId).toList());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void repositoriesRejectNullEntitiesAndInvalidIds() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        assertThrows(IllegalArgumentException.class, () -> repository.insert(null));
        assertThrows(IllegalArgumentException.class,
                () -> repository.insert(user(" ", "blank@example.com")));
        assertThrows(IllegalArgumentException.class, () -> repository.findById(null));
        assertThrows(IllegalArgumentException.class, () -> repository.findById(" "));
    }

    @Test
    void snapshotsDoNotExposeInternalStorage() {
        InMemoryServiceRepository repository = new InMemoryServiceRepository();
        repository.insert(service("s-1", "One"));
        assertThrows(UnsupportedOperationException.class, () -> repository.storageSnapshot().clear());
        assertEquals(1, repository.findAll().size());
    }

    private static <T> void assertDuplicateProtected(Repository<T, String> repository, T first, T replacement) {
        assertTrue(repository.insert(first));
        assertFalse(repository.insert(replacement));
        assertEquals(1, repository.findAll().size());
        assertSame(first, repository.findAll().getFirst());
    }

    private static User user(String id, String email) {
        return User.withEncodedPassword(id, "Test User", email, "0821234567", "encoded",
                new Role("customer", "CUSTOMER", "Customer", null));
    }

    private static Vehicle vehicle(String id, String plate) {
        return new Vehicle(id, plate, "SEDAN", "Brand", "Model", "White", "");
    }

    private static Service service(String id, String name) {
        return new Service(id, name, "Description", BigDecimal.TEN, 30);
    }

    private static Booking booking(String id, User user, Vehicle vehicle, Service service) {
        return new Booking(id, user, vehicle, service, LocalDateTime.now().plusDays(1), "");
    }
}
