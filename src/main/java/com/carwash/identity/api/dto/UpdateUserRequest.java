package com.carwash.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 120) @Schema(example = "Alex Customer") String fullName,
        @NotBlank @Email @Size(max = 254) @Schema(example = "alex@example.com") String email,
        @NotBlank @Size(max = 40) @Schema(example = "+27 82 123 4567") String phone
) {
    public UpdateUserRequest {
        fullName = trim(fullName);
        email = trim(email);
        phone = trim(phone);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
