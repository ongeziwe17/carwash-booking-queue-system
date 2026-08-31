package com.carwash.access.application;

import com.carwash.access.infrastructure.JwtSecurityProperties;
import com.carwash.identity.domain.User;
import com.carwash.identity.application.TenantMembershipQuery;
import com.carwash.identity.domain.RoleCatalog;
import com.carwash.identity.domain.RoleName;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtTokenService {

    public static final String TENANT_ID_CLAIM = "tenant_id";

    private final JwtEncoder encoder;
    private final JwtSecurityProperties properties;
    private final Clock clock;
    private final TenantMembershipQuery memberships;

    public JwtTokenService(
            JwtEncoder encoder,
            JwtSecurityProperties properties,
            Clock clock,
            TenantMembershipQuery memberships
    ) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.memberships = memberships;
    }

    public IssuedToken issue(User user) {
        RoleName role = RoleCatalog.name(user.getRole());
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getUserId())
                .claim("role", role.name())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString());

        if (role == RoleName.STAFF || role == RoleName.BUSINESS_OWNER) {
            String businessId = memberships.findByUserId(user.getUserId())
                    .map(com.carwash.identity.domain.TenantMembership::businessId)
                    .orElseThrow(InvalidCredentialsException::new);
            claims.claim(TENANT_ID_CLAIM, businessId);
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(value, expiresAt, properties.accessTokenTtl().toSeconds());
    }

    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {
    }
}
