package com.carwash.testsupport;

import com.carwash.access.application.TenantAccessContext;
import com.carwash.identity.domain.RoleName;

/** Explicit authenticated scopes used by non-API service fixtures. */
public final class TestAccess {

    private static final TenantAccessContext PLATFORM_ADMINISTRATOR =
            new TenantAccessContext("test-platform-administrator", RoleName.PLATFORM_ADMIN, null);

    private TestAccess() {
    }

    public static TenantAccessContext platformAdministrator() {
        return PLATFORM_ADMINISTRATOR;
    }

    public static TenantAccessContext customer(String userId) {
        return new TenantAccessContext(userId, RoleName.CUSTOMER, null);
    }
}
