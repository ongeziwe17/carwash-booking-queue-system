package com.carwash.security;

import com.carwash.config.JwtSecurityProperties;
import com.carwash.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final JwtSecurityProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, JwtSecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(user.getUserId())
                .issuedAt(issuedAt).expiresAt(expiresAt).id(UUID.randomUUID().toString()).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, expiresAt, properties.accessTokenTtl().toSeconds());
    }

    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {}
}