package com.carwash.identity.api;

import com.carwash.identity.domain.Role;
import com.carwash.identity.domain.User;

import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.identity.api.dto.AssignRoleRequest;
import com.carwash.identity.api.dto.UserResponse;
import com.carwash.identity.api.mapper.UserMapper;
import com.carwash.identity.application.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Administration", description = "Platform-administrator operations for managing users.")
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserManagementService users;
    private final UserMapper mapper;

    public AdminUserController(UserManagementService users, UserMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Assign a role to a user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role assigned",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or role-assignment rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse assignRole(
            @PathVariable @NotBlank @Size(max = 64) String userId,
            @Valid @RequestBody AssignRoleRequest request
    ) {
        return mapper.toResponse(users.assignRole(userId, request.roleName()));
    }
}
