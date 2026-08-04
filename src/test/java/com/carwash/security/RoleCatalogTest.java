package com.carwash.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RoleCatalogTest {
    @Test void customerHasOnlySelfServicePermissions() {
        assertThat(RoleCatalog.permissions(RoleName.CUSTOMER))
                .contains(Permission.USER_SELF_MANAGE, Permission.SERVICE_READ)
                .doesNotContain(Permission.USER_ADMIN, Permission.SERVICE_MANAGE, Permission.ROLE_ASSIGN);
    }
    @Test void staffOwnerAndAdminHierarchyIsMonotonic() {
        assertThat(RoleCatalog.permissions(RoleName.STAFF)).containsAll(RoleCatalog.permissions(RoleName.CUSTOMER));
        assertThat(RoleCatalog.permissions(RoleName.BUSINESS_OWNER)).containsAll(RoleCatalog.permissions(RoleName.STAFF));
        assertThat(RoleCatalog.permissions(RoleName.PLATFORM_ADMIN)).containsAll(RoleCatalog.permissions(RoleName.BUSINESS_OWNER));
    }
    @Test void builtInRoleContainsOnlyCatalogPermissions() {
        assertThat(RoleCatalog.role(RoleName.PLATFORM_ADMIN).getPermissions())
                .containsExactlyInAnyOrderElementsOf(RoleCatalog.permissions(RoleName.PLATFORM_ADMIN).stream()
                        .map(Enum::name).toList());
    }
}
