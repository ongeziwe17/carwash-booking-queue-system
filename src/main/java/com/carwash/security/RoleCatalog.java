package com.carwash.security;

import com.carwash.domain.Role;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RoleCatalog {
    private static final Map<RoleName, Set<Permission>> ROLES = Map.of(
            RoleName.CUSTOMER, EnumSet.of(Permission.USER_SELF_MANAGE, Permission.VEHICLE_SELF_MANAGE,
                    Permission.SERVICE_READ, Permission.BOOKING_SELF_MANAGE, Permission.QUEUE_SELF_READ,
                    Permission.NOTIFICATION_SELF_READ),
            RoleName.STAFF, EnumSet.of(Permission.USER_SELF_MANAGE, Permission.VEHICLE_SELF_MANAGE,
                    Permission.VEHICLE_OPERATE, Permission.SERVICE_READ, Permission.BOOKING_SELF_MANAGE,
                    Permission.BOOKING_OPERATE, Permission.QUEUE_SELF_READ, Permission.QUEUE_OPERATE,
                    Permission.NOTIFICATION_SELF_READ),
            RoleName.BUSINESS_OWNER, EnumSet.of(Permission.USER_SELF_MANAGE, Permission.VEHICLE_SELF_MANAGE,
                    Permission.VEHICLE_OPERATE, Permission.SERVICE_READ, Permission.SERVICE_MANAGE,
                    Permission.BOOKING_SELF_MANAGE, Permission.BOOKING_OPERATE, Permission.QUEUE_SELF_READ,
                    Permission.QUEUE_OPERATE, Permission.NOTIFICATION_SELF_READ, Permission.REPORT_READ),
            RoleName.PLATFORM_ADMIN, EnumSet.allOf(Permission.class));

    private RoleCatalog() { }
    public static Set<Permission> permissions(RoleName role) { return Set.copyOf(ROLES.get(role)); }
    public static Role role(RoleName name) {
        return new Role("builtin:" + name.name(), name.name(), "Built-in " + name.name().toLowerCase(Locale.ROOT),
                permissions(name).stream().map(Enum::name).collect(Collectors.toSet()));
    }
    public static RoleName name(Role role) {
        if (role == null || role.getRoleName() == null) throw new IllegalArgumentException("A valid role is required");
        return RoleName.valueOf(role.getRoleName());
    }
}
