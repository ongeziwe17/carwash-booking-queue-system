package com.carwash.security;

import com.carwash.config.PasswordSecurityProperties;
import com.carwash.service.exception.BusinessRuleViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserCredentialService {
    private final PasswordEncoder passwordEncoder;
    private final PasswordSecurityProperties properties;

    public UserCredentialService(PasswordEncoder passwordEncoder, PasswordSecurityProperties properties) {
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    public void validatePolicy(String rawPassword) {
        if (rawPassword == null) throw new BusinessRuleViolationException("Password is required");
        if (rawPassword.isBlank()) throw new BusinessRuleViolationException("Password must not be blank");
        if (rawPassword.length() < properties.minLength()) {
            throw new BusinessRuleViolationException("Password must be at least " + properties.minLength() + " characters");
        }
        if (rawPassword.length() > properties.maxLength()) {
            throw new BusinessRuleViolationException("Password must not exceed " + properties.maxLength() + " characters");
        }
    }

    public String encode(String rawPassword) {
        validatePolicy(rawPassword);
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) return false;
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException malformedCredential) {
            return false;
        }
    }
}
