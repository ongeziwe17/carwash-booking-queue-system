package com.carwash.service;

import com.carwash.config.PasswordSecurityProperties;
import com.carwash.repository.inmemory.InMemoryUserRepository;
import com.carwash.security.RoleName;
import com.carwash.security.UserCredentialService;
import com.carwash.service.command.CreateUserCommand;
import com.carwash.service.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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
                )
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
