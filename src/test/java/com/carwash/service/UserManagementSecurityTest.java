package com.carwash.service;

import com.carwash.config.PasswordSecurityProperties;
import com.carwash.domain.User;
import com.carwash.enums.AccountStatus;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;
import com.carwash.repository.inmemory.InMemoryUserRepository;
import com.carwash.security.RoleName;
import com.carwash.security.UserCredentialService;
import com.carwash.service.command.CreateUserCommand;
import com.carwash.service.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserManagementSecurityTest {

    private InMemoryUserRepository repository;
    private UserManagementService users;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
        users = new UserManagementService(
                repository,
                new UserCredentialService(
                        new BCryptPasswordEncoder(4),
                        new PasswordSecurityProperties(4, 12, 72)
                ),
                null,
                null,
                null,
                new InMemoryDataCoordinator()
        );
    }

    @Test
    void deletingLastActivePlatformAdministratorIsRejected() {
        createPlatformAdministrator("sole-admin", "sole-admin@example.com");

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> users.deleteUser("sole-admin")
        );

        assertEquals("The last active platform administrator cannot be deleted", exception.getMessage());
        assertTrue(repository.findById("sole-admin").isPresent());
    }

    @Test
    void oneAdministratorCanBeDeletedWhenAnotherActiveAdministratorRemains() {
        createPlatformAdministrator("first-admin", "first-admin@example.com");
        createPlatformAdministrator("second-admin", "second-admin@example.com");

        users.deleteUser("first-admin");

        assertFalse(repository.findById("first-admin").isPresent());
        assertTrue(repository.findById("second-admin").isPresent());
    }

    @Test
    void concurrentAdministratorDeletionsPreserveOneActiveAdministrator() throws Exception {
        createPlatformAdministrator("concurrent-delete-first", "concurrent-delete-first@example.com");
        createPlatformAdministrator("concurrent-delete-second", "concurrent-delete-second@example.com");

        List<String> outcomes = runConcurrently(
                () -> users.deleteUser("concurrent-delete-first"),
                () -> users.deleteUser("concurrent-delete-second")
        );

        assertEquals(1, outcomes.stream().filter("succeeded"::equals).count());
        assertEquals(1, outcomes.stream().filter("rejected"::equals).count());
        assertEquals(1, activePlatformAdministratorCount());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void concurrentAdministratorDeletionAndDemotionPreserveOneActiveAdministrator() throws Exception {
        createPlatformAdministrator("concurrent-mixed-first", "concurrent-mixed-first@example.com");
        createPlatformAdministrator("concurrent-mixed-second", "concurrent-mixed-second@example.com");

        List<String> outcomes = runConcurrently(
                () -> users.deleteUser("concurrent-mixed-first"),
                () -> users.assignRole("concurrent-mixed-second", RoleName.CUSTOMER)
        );

        assertEquals(1, outcomes.stream().filter("succeeded"::equals).count());
        assertEquals(1, outcomes.stream().filter("rejected"::equals).count());
        assertEquals(1, activePlatformAdministratorCount());
    }

    private List<String> runConcurrently(Runnable firstAction, Runnable secondAction) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> first = executor.submit(concurrentAttempt(firstAction, ready, start));
            Future<String> second = executor.submit(concurrentAttempt(secondAction, ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Concurrent operations did not become ready");
            start.countDown();

            return List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Callable<String> concurrentAttempt(
            Runnable action,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                action.run();
                return "succeeded";
            } catch (BusinessRuleViolationException exception) {
                return "rejected";
            }
        };
    }

    private long activePlatformAdministratorCount() {
        return repository.findAll().stream()
                .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .map(User::getRole)
                .filter(role -> role != null && RoleName.PLATFORM_ADMIN.name().equals(role.getRoleName()))
                .count();
    }

    private void createPlatformAdministrator(String userId, String email) {
        users.createUser(new CreateUserCommand(
                userId,
                "Platform Administrator",
                email,
                "0821234567",
                "LocalTestPassword123!"
        ));
        users.assignRole(userId, RoleName.PLATFORM_ADMIN);
    }
}
