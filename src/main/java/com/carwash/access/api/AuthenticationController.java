package com.carwash.access.api;

import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.access.api.dto.AuthResponse;
import com.carwash.access.api.dto.LoginRequest;
import com.carwash.identity.api.dto.UserResponse;
import com.carwash.identity.api.mapper.UserMapper;
import com.carwash.access.application.AuthenticationResult;
import com.carwash.access.application.UserAuthenticationService;
import com.carwash.identity.application.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Authentication", description = "Authentication and current-user operations.")
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final UserAuthenticationService authentication;
    private final UserMapper mapper;
    private final UserManagementService users;

    public AuthenticationController(
            UserAuthenticationService authentication,
            UserMapper mapper,
            UserManagementService users
    ) {
        this.authentication = authentication;
        this.mapper = mapper;
        this.users = users;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
            summary = "Exchange email and password for a short-lived bearer token",
            description = "The access token expires at expiresAt; send it as Authorization: Bearer <token>."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or malformed login request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid email or password",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticationResult result = authentication.authenticate(request.email(), request.password());
        return new AuthResponse(
                result.accessToken(),
                result.expiresInSeconds(),
                result.expiresAt(),
                mapper.toResponse(result.user())
        );
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated user returned",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Authenticated user not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse me(Authentication principal) {
        return mapper.toResponse(users.findById(principal.getName()));
    }
}
