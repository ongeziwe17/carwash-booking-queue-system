package com.carwash.access.application;

import com.carwash.access.infrastructure.PasswordSecurityProperties;
import com.carwash.identity.application.CredentialService;
import com.carwash.shared.exception.BusinessRuleViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class UserCredentialService implements CredentialService {

    private static final String INVALID_AUTHENTICATION_CANDIDATE = "invalid-authentication-candidate";

    private final PasswordEncoder passwordEncoder;
    private final PasswordSecurityProperties properties;

    public UserCredentialService(
            PasswordEncoder passwordEncoder,
            PasswordSecurityProperties properties
    ) {
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    public void validatePolicy(String rawPassword) {
        if (rawPassword == null) {
            throw new BusinessRuleViolationException("Password is required");
        }
        if (rawPassword.isBlank()) {
            throw new BusinessRuleViolationException("Password must not be blank");
        }
        if (rawPassword.length() < properties.minLength()) {
            throw new BusinessRuleViolationException(
                    "Password must be at least " + properties.minLength() + " characters"
            );
        }
        if (utf8Length(rawPassword) > properties.maxUtf8Bytes()) {
            throw new BusinessRuleViolationException(
                    "Password must not exceed " + properties.maxUtf8Bytes() + " bytes when UTF-8 encoded"
            );
        }
    }

    public String encode(String rawPassword) {
        validatePolicy(rawPassword);
        return passwordEncoder.encode(rawPassword);
    }

    String createDummyEncoding() {
        return passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }

        String candidate = rawPassword;
        if (candidate == null || utf8Length(candidate) > properties.maxUtf8Bytes()) {
            candidate = INVALID_AUTHENTICATION_CANDIDATE;
        }

        try {
            return passwordEncoder.matches(candidate, encodedPassword);
        } catch (IllegalArgumentException malformedCredential) {
            return false;
        }
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
