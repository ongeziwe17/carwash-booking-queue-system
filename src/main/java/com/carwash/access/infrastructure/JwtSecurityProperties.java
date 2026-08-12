package com.carwash.access.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "carwash.security.jwt")
public record JwtSecurityProperties(
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotBlank String secret
) {
    public JwtSecurityProperties {
        if (accessTokenTtl != null && (accessTokenTtl.isZero() || accessTokenTtl.isNegative())) {
            throw new IllegalArgumentException("JWT access token TTL must be positive");
        }
    }
}
