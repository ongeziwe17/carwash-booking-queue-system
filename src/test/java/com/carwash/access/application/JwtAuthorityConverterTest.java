package com.carwash.access.application;

import com.carwash.access.application.JwtAuthorityConverter;
import com.carwash.identity.domain.Permission;
import com.carwash.identity.domain.RoleCatalog;
import com.carwash.identity.domain.RoleName;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthorityConverterTest {

    private static final Instant TEST_ISSUED_AT = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant TEST_EXPIRES_AT = TEST_ISSUED_AT.plusSeconds(300);

    private final JwtAuthorityConverter converter = new JwtAuthorityConverter();

    @ParameterizedTest
    @EnumSource(RoleName.class)
    void eachRoleReceivesOnlyCurrentCatalogueAuthorities(RoleName roleName) {
        AbstractAuthenticationToken authentication = converter.convert(jwt(
                roleName.name(),
                List.of(Permission.ROLE_ASSIGN.name(), Permission.SERVICE_MANAGE.name())
        ));

        Set<String> expected = new HashSet<>();
        expected.add("ROLE_" + roleName.name());
        RoleCatalog.permissions(roleName)
                .forEach(permission -> expected.add("PERM_" + permission.name()));

        assertThat(authorityNames(authentication))
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void injectedPermissionClaimCannotGrantAdditionalAccess() {
        AbstractAuthenticationToken authentication = converter.convert(jwt(
                RoleName.CUSTOMER.name(),
                List.of(Permission.ROLE_ASSIGN.name(), Permission.SERVICE_MANAGE.name())
        ));

        assertThat(authorityNames(authentication))
                .contains("ROLE_CUSTOMER", "PERM_SERVICE_READ")
                .doesNotContain("PERM_ROLE_ASSIGN", "PERM_SERVICE_MANAGE")
                .containsExactlyInAnyOrderElementsOf(expectedAuthorities(RoleName.CUSTOMER));
    }

    @Test
    void unknownRoleProducesNoAuthorities() {
        AbstractAuthenticationToken authentication = converter.convert(jwt(
                "SUPER_ADMIN",
                List.of(Permission.ROLE_ASSIGN.name())
        ));

        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void missingRoleProducesNoAuthorities() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("test-user")
                .issuedAt(TEST_ISSUED_AT)
                .expiresAt(TEST_EXPIRES_AT)
                .build();

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication.getAuthorities()).isEmpty();
    }

    private Jwt jwt(String roleName, List<String> injectedPermissions) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("test-user")
                .claim("role", roleName)
                .claim("permissions", injectedPermissions)
                .issuedAt(TEST_ISSUED_AT)
                .expiresAt(TEST_EXPIRES_AT)
                .build();
    }

    private Set<String> authorityNames(AbstractAuthenticationToken authentication) {
        Set<String> authorities = new HashSet<>();
        authentication.getAuthorities()
                .forEach(authority -> authorities.add(authority.getAuthority()));
        return authorities;
    }

    private Set<String> expectedAuthorities(RoleName roleName) {
        Set<String> authorities = new HashSet<>();
        authorities.add("ROLE_" + roleName.name());
        RoleCatalog.permissions(roleName)
                .forEach(permission -> authorities.add("PERM_" + permission.name()));
        return authorities;
    }
}
