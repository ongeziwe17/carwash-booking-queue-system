package com.carwash.security;

import com.carwash.domain.User;
import java.time.Instant;

public record AuthenticationResult(User user, String accessToken, Instant expiresAt, long expiresInSeconds) {}
