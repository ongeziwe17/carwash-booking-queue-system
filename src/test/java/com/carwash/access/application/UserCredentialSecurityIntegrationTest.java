package com.carwash.access.application;

import com.carwash.access.application.UserCredentialService;

import com.carwash.identity.domain.User;
import com.carwash.identity.domain.UserRepository;
import com.carwash.identity.application.UserManagementService;
import com.carwash.identity.application.CreateUserCommand;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.UserFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class UserCredentialSecurityIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired UserManagementService userManagementService;
    @Autowired UserCredentialService credentialService;
    @Autowired UserRepository userRepository;

    @Test
    void registrationStoresSaltedEncodedCredentialsThatCanBeVerified() {
        String raw = UserFixtureBuilder.DEFAULT_PASSWORD;
        User first = register(raw);
        User second = register(raw);
        assertNotEquals(raw, first.getEncodedPassword());
        assertNotEquals(first.getEncodedPassword(), second.getEncodedPassword());
        assertTrue(credentialService.matches(raw, first.getEncodedPassword()));
        assertFalse(credentialService.matches("WrongPassword123!", first.getEncodedPassword()));
        assertFalse(credentialService.matches(raw, "not-a-valid-bcrypt-value"));
        assertEquals(first.getEncodedPassword(), userRepository.findById(first.getUserId()).orElseThrow().getEncodedPassword());
    }

    @Test
    void policyChecksNullBlankAndConfiguredUtf8BoundariesWithoutTrimming() {
        assertThrows(BusinessRuleViolationException.class, () -> credentialService.validatePolicy(null));
        assertThrows(BusinessRuleViolationException.class, () -> credentialService.validatePolicy("   "));
        assertThrows(BusinessRuleViolationException.class, () -> credentialService.validatePolicy("12345678901"));
        assertDoesNotThrow(() -> credentialService.validatePolicy("x".repeat(72)));
        assertThrows(BusinessRuleViolationException.class, () -> credentialService.validatePolicy("x".repeat(73)));
        assertThrows(BusinessRuleViolationException.class, () -> credentialService.validatePolicy("\u20ac".repeat(25)));
        assertThrows(BusinessRuleViolationException.class, () -> credentialService.encode("x".repeat(72) + "first suffix"));
        assertThrows(BusinessRuleViolationException.class, () -> credentialService.encode("x".repeat(72) + "second suffix"));
        assertDoesNotThrow(() -> credentialService.validatePolicy("x".repeat(12)));
        String withoutSpace = credentialService.encode("Password123!");
        assertFalse(credentialService.matches("Password123! ", withoutSpace));
    }

    @Test
    void domainSerializationOmitsEncodedCredential() throws Exception {
        User user = register(UserFixtureBuilder.DEFAULT_PASSWORD);
        String json = objectMapper.writeValueAsString(user);
        assertFalse(json.contains("encodedPassword"));
        assertFalse(json.contains(user.getEncodedPassword()));
    }

    private User register(String rawPassword) {
        String id = ids.user();
        return userManagementService.createUser(new CreateUserCommand(id, "Security User", ids.emailFor(id),
                "0821234567", rawPassword));
    }
}
