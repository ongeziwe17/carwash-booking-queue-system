package com.carwash.identity.api.dto;

import com.carwash.access.application.RoleName;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(@NotNull(message = "roleName is required") RoleName roleName) {
}
