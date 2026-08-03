package com.carwash.api;

import com.carwash.api.dto.*;
import com.carwash.api.mapper.UserMapper;
import com.carwash.security.AuthenticationResult;
import com.carwash.security.UserAuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final UserAuthenticationService authentication;
    private final UserMapper mapper;

    public AuthenticationController(UserAuthenticationService authentication, UserMapper mapper) {
        this.authentication = authentication;
        this.mapper = mapper;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Exchange email and password for a short-lived bearer token",
            description = "The access token expires at expiresAt; send it as Authorization: Bearer <token>.")
    @ApiResponse(responseCode = "200", description = "Authentication successful")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticationResult result = authentication.authenticate(request.email(), request.password());
        return new AuthResponse(result.accessToken(), result.expiresInSeconds(), result.expiresAt(),
                mapper.toResponse(result.user()));
    }
}
