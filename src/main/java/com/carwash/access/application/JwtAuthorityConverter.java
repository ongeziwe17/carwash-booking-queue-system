package com.carwash.access.application;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public class JwtAuthorityConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        parseRole(jwt.getClaimAsString("role")).ifPresent(roleName -> {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName.name()));
            RoleCatalog.permissions(roleName).stream()
                    .map(Enum::name)
                    .sorted()
                    .map(permission -> new SimpleGrantedAuthority("PERM_" + permission))
                    .forEach(authorities::add);
        });

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private Optional<RoleName> parseRole(String roleClaim) {
        if (roleClaim == null || roleClaim.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(RoleName.valueOf(roleClaim));
        } catch (IllegalArgumentException unknownRole) {
            return Optional.empty();
        }
    }
}
