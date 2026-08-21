package com.carwash.identity.infrastructure;

import java.io.Serializable;
import java.util.Objects;

public final class RolePermissionId implements Serializable {
    public String roleId;
    public String permission;

    public RolePermissionId() { }

    public RolePermissionId(String roleId, String permission) {
        this.roleId = roleId;
        this.permission = permission;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RolePermissionId that)) return false;
        return Objects.equals(roleId, that.roleId) && Objects.equals(permission, that.permission);
    }

    @Override public int hashCode() { return Objects.hash(roleId, permission); }
}
