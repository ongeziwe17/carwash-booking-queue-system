package com.carwash.api.dto;

import com.carwash.enums.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Safe public user representation; credentials and related domain collections are omitted.")
public record UserResponse(
        String userId,
        String fullName,
        String email,
        String phone,
        AccountStatus accountStatus,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        @Schema(nullable = true, description = "Assigned role name, when RBAC assignment is available") String roleName
) {
}
