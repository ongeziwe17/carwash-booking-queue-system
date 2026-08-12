package com.carwash.identity.api.dto;

import com.carwash.identity.domain.RoleName;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(@NotNull(message = "roleName is required") RoleName roleName) {
}
