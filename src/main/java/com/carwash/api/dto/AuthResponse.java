package com.carwash.api.dto;

import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds,
                           Instant expiresAt, UserResponse user) {
    public AuthResponse(String accessToken, long expiresInSeconds, Instant expiresAt, UserResponse user) {
        this(accessToken, "Bearer", expiresInSeconds, expiresAt, user);
    }
}
