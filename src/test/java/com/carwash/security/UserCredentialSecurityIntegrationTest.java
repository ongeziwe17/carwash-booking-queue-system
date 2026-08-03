package com.carwash.security;

import com.carwash.domain.User;
import com.carwash.repository.UserRepository;
import com.carwash.service.UserManagementService;
import com.carwash.service.command.CreateUserCommand;
import com.carwash.service.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserCredentialSecurityIntegrationTest {
    @Autowired UserManagementService userManagementService;
    @Autowired UserCredentialService credentialService;
    @Autowired UserRepository userRepository;
    @Autowired ObjectMapper objectMapper;

    @Test
    void registrationStoresSaltedEncodedCredentialsThatCanBeVerified() {
        String raw = "LocalTestPassword123!";
        User first = register("encoded-first", raw);
        User second = register("encoded-second", raw);

        assertNotEquals(raw, first.getEncodedPassword());
        assertNotEquals(first.getEncodedPassword(), second.getEncodedPassword());
        assertTrue(credentialService.matches(raw, first.getEncodedPassword()));
        assertFalse(credentialService.matches("WrongPassword123!", first.getEncodedPassword()));
        assertFalse(credentialService.matches(raw, "not-a-valid-bcrypt-value"));
        assertEquals(first.getEncodedPassword(), userRepository.findById(first.getUserId()).orElseThrow().getEncodedPassword());
    }

    @Test
    void policyChecksNullBlankAndConfiguredLengthBoundariesWithoutTrimming() {
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
        User user = register("serialized", "LocalTestPassword123!");
        String json = objectMapper.writeValueAsString(user);

        assertFalse(json.contains("encodedPassword"));
        assertFalse(json.contains(user.getEncodedPassword()));
    }

    private User register(String prefix, String rawPassword) {
        String id = prefix + "-" + UUID.randomUUID();
        return userManagementService.createUser(new CreateUserCommand(id, "Security User", id + "@example.com",
                "0821234567", rawPassword));
    }
}
