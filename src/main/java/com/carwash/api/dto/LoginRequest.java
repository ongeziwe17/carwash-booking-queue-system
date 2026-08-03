package com.carwash.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 256)
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, description = "Account password") String password
) {
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
