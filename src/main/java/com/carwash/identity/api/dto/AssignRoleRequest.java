package com.carwash.identity.api.dto;

import com.carwash.identity.domain.RoleName;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignRoleRequest(
        @NotNull(message = "roleName is required") RoleName roleName,
        @Size(max = 64) String businessId
) {
}
