package com.carwash.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignTenantMembershipRequest(
        @NotBlank(message = "businessId is required") @Size(max = 64) String businessId
) {
}
