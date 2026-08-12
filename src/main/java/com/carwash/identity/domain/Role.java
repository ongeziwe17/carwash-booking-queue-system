package com.carwash.identity.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
public class Role {
    private String roleId;
    private String roleName;
    private String description;
    private Set<String> permissions = new HashSet<>();

    public Role() {
    }

    public Role(String roleId, String roleName, String description, Set<String> permissions) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.description = description;
        if (permissions != null) {
            this.permissions = new HashSet<>(permissions);
        }
    }

    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission.trim().toUpperCase());
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = Objects.requireNonNullElseGet(permissions, HashSet::new);
    }
}
