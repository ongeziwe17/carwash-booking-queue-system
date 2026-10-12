package com.carwash.identity.api;

import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.identity.api.dto.UpdateUserRequest;
import com.carwash.identity.api.dto.UserResponse;
import com.carwash.identity.api.mapper.UserMapper;
import com.carwash.identity.domain.User;
import com.carwash.identity.application.UserManagementService;
import com.carwash.identity.application.CreateUserCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.carwash.shared.api.PageResponse;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@Validated
@Tag(name = "Users", description = "Operations for registering and managing safe user profiles.")
@RequestMapping("/api/users")
public class UserController {

    private final UserManagementService service;
    private final UserMapper mapper;
    private final com.carwash.access.application.TenantAccessContextProvider tenantAccess;

    public UserController(UserManagementService service, UserMapper mapper, com.carwash.access.application.TenantAccessContextProvider tenantAccess) {
        this.service = service;
        this.mapper = mapper;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "List users")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Invalid pagination, filter or sort",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "200", description = "Users returned", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @io.swagger.v3.oas.annotations.Parameters({
        @io.swagger.v3.oas.annotations.Parameter(name = "id", description = "Exact resource ID; narrows authorized scope only.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", maxLength = 64)),
        @io.swagger.v3.oas.annotations.Parameter(name = "page", description = "Zero-based page; default 0. Invalid or overflowing offsets return ApiErrorResponse 400.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", minimum = "0", defaultValue = "0")),
        @io.swagger.v3.oas.annotations.Parameter(name = "size", description = "Page size, default 20; rejects values outside 1-100.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")),
        @io.swagger.v3.oas.annotations.Parameter(name = "sort", description = "Repeat up to three field,asc|desc terms from the endpoint allowlist; unique ID tie-breaker and NULLS LAST. See API-PAGINATION.md.", style = io.swagger.v3.oas.annotations.enums.ParameterStyle.FORM, explode = io.swagger.v3.oas.annotations.enums.Explode.TRUE, array = @io.swagger.v3.oas.annotations.media.ArraySchema(maxItems = 3, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))),
        @io.swagger.v3.oas.annotations.Parameter(name = "accountStatus", description = "Exact PENDING, ACTIVE, SUSPENDED or DEACTIVATED; sorts id,name,createdAt.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    })
    public PageResponse<UserResponse> getAll(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam org.springframework.util.MultiValueMap<String, String> parameters
    ) {
        return PageResponse.from(service.findPage(tenantAccess.current(), com.carwash.identity.application.UserListPolicy.POLICY.parse(parameters)).map(mapper::toListResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.isSelf(authentication, #id)")
    @Operation(summary = "Get user by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User returned",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid identifier",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse getById(@PathVariable @NotBlank @Size(max = 64) String id) {
        return mapper.toResponse(service.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(
            summary = "Register a customer",
            description = "Public registration always creates a CUSTOMER; privileged roles cannot be selected here."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or duplicate email",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        User created = service.createUser(new CreateUserCommand(
                request.userId(),
                request.fullName(),
                request.email(),
                request.phone(),
                request.password()
        ));
        return mapper.toResponse(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.isSelf(authentication, #id)")
    @Operation(summary = "Update a user profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or duplicate email",
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
    public UserResponse update(
            @PathVariable @NotBlank @Size(max = 64) String id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return mapper.toResponse(service.updateUser(id, request.fullName(), request.email(), request.phone()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.isSelf(authentication, #id)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid identifier or user-deletion rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public void delete(@PathVariable @NotBlank @Size(max = 64) String id) {
        service.deleteUser(id);
    }
}
