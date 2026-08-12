package com.carwash.access.application;

import com.carwash.identity.domain.User;
import java.time.Instant;

public record AuthenticationResult(User user, String accessToken, Instant expiresAt, long expiresInSeconds) {}
