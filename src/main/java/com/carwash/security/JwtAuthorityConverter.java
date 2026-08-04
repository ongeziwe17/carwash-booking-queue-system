package com.carwash.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;

public class JwtAuthorityConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override public AbstractAuthenticationToken convert(Jwt jwt) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        String role = jwt.getClaimAsString("role");
        if (role != null) authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        if (permissions != null) permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority("PERM_" + p)));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
