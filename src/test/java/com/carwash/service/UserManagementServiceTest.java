package com.carwash.service;

import com.carwash.domain.Role;
import com.carwash.domain.User;
import com.carwash.enums.AccountStatus;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserManagementServiceTest extends ServiceTestSupport {

    @Test
    void userCreationSucceeds() {
        String id = ids.user();
        User created = register(id, ids.emailFor(id));
        assertEquals(id, created.getUserId());
        assertEquals(AccountStatus.ACTIVE, created.getAccountStatus());
        assertNotNull(created.getCreatedAt());
    }

    @Test
    void userCreationNormalizesProfileAndEmail() {
        String id = ids.user();
        String email = ids.emailFor(id);
        User created = register(" " + id + " ", " " + email.toUpperCase() + " ", " Jane Doe ", " 123 ");
        assertEquals(id, created.getUserId());
        assertEquals("Jane Doe", created.getFullName());
        assertEquals(email, created.getEmail());
        assertEquals("123", created.getPhone());
    }

    @Test
    void userCreationRejectsCaseAndWhitespaceDuplicateEmail() {
        String firstId = ids.user();
        String secondId = ids.user();
        String email = ids.emailFor(firstId);
        register(firstId, email);
        assertThrows(BusinessRuleViolationException.class,
                () -> register(secondId, " " + email.toUpperCase() + " "));
    }

    @Test
    void profileUpdatePreservesServerControlledAndSensitiveFields() {
        String id = ids.user();
        User created = register(id, ids.emailFor(id));
        LocalDateTime createdAt = created.getCreatedAt();
        String encodedPassword = created.getEncodedPassword();
        Role originalRole = created.getRole();

        String updatedEmail = "updated-" + ids.emailFor(id);
        User updated = userService.updateUser(id, " Janet Doe ", " " + updatedEmail.toUpperCase() + " ", " 456 ");

        assertEquals("Janet Doe", updated.getFullName());
        assertEquals(updatedEmail, updated.getEmail());
        assertEquals("456", updated.getPhone());
        assertEquals(encodedPassword, updated.getEncodedPassword());
        assertNotNull(updated.getRole());
        assertEquals("CUSTOMER", updated.getRole().getRoleName());
        assertSame(originalRole, updated.getRole());
        assertEquals(AccountStatus.ACTIVE, updated.getAccountStatus());
        assertEquals(createdAt, updated.getCreatedAt());
    }

    @Test
    void userCreationFailsWithBlankEmail() {
        String id = ids.user();
        assertThrows(BusinessRuleViolationException.class, () -> register(id, " "));
    }

    @Test
    void userCreationFailsWithBlankFullName() {
        String id = ids.user();
        assertThrows(BusinessRuleViolationException.class,
                () -> register(id, ids.emailFor(id), " ", "123"));
    }

    @Test
    void userLookupMissingIdThrows() {
        assertThrows(ResourceNotFoundException.class, () -> userService.findById(ids.user()));
    }

    @Test
    void duplicateEmailThrowsException() {
        String firstId = ids.user();
        String secondId = ids.user();
        String email = ids.emailFor(firstId);
        register(firstId, email);
        assertThrows(BusinessRuleViolationException.class, () -> register(secondId, email));
    }

    @Test
    void updateToDuplicateEmailThrowsException() {
        String firstId = ids.user();
        String secondId = ids.user();
        User first = register(firstId, ids.emailFor(firstId));
        User second = register(secondId, ids.emailFor(secondId));
        second.setEmail(first.getEmail());
        assertThrows(BusinessRuleViolationException.class, () -> userService.updateUser(second));
    }

    @Test
    void duplicateEmailComparisonIsCaseInsensitive() {
        String firstId = ids.user();
        String secondId = ids.user();
        String email = ids.emailFor(firstId);
        register(firstId, email.toUpperCase());
        assertThrows(BusinessRuleViolationException.class, () -> register(secondId, email));
    }
}
