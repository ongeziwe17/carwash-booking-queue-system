package com.carwash.api.dto;

import com.carwash.security.RoleName;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(@NotNull(message = "roleName is required") RoleName roleName) {
}
