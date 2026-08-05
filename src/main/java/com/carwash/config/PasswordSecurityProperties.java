package com.carwash.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "carwash.security.password")
public record PasswordSecurityProperties(
        @Min(4) @Max(31) int bcryptStrength,
        @Min(1) int minLength,
        @Min(1) @Max(72) int maxUtf8Bytes
) {
    public PasswordSecurityProperties {
        if (minLength > maxUtf8Bytes) {
            throw new IllegalArgumentException("Password minimum length must not exceed maximum UTF-8 bytes");
        }
    }
}
