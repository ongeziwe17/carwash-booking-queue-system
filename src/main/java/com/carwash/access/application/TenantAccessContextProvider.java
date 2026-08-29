package com.carwash.access.application;

import com.carwash.identity.domain.RoleName;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves the already-validated JWT once into a bounded application access context. */
@Component
public final class TenantAccessContextProvider {

    public TenantAccessContext current() {
        return from(SecurityContextHolder.getContext().getAuthentication());
    }

    public TenantAccessContext from(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)
                || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        Jwt jwt = token.getToken();
        try {
            RoleName role = RoleName.valueOf(jwt.getClaimAsString("role"));
            String tenantId = jwt.getClaimAsString(JwtTokenService.TENANT_ID_CLAIM);
            return new TenantAccessContext(jwt.getSubject(), role, tenantId);
        } catch (RuntimeException invalidClaim) {
            throw new AuthenticationCredentialsNotFoundException("Validated authentication context is invalid",
                    invalidClaim);
        }
    }
}
