package com.carwash.api;

import com.carwash.api.dto.ApiErrorResponse;
import com.carwash.api.dto.CreateUserRequest;
import com.carwash.api.dto.UpdateUserRequest;
import com.carwash.api.dto.UserResponse;
import com.carwash.api.mapper.UserMapper;
import com.carwash.domain.User;
import com.carwash.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Users", description = "Operations for registering and managing safe user profiles.")
@RequestMapping("/api/users")
public class UserController {
    private final UserManagementService service;
    private final UserMapper mapper;

    public UserController(UserManagementService service, UserMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List users")
    @ApiResponse(responseCode = "200", description = "Users returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class))))
    public List<UserResponse> getAll() {
        return mapper.toResponseList(service.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User returned", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse getById(@PathVariable String id) {
        return mapper.toResponse(service.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or duplicate email", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        User created = service.createUser(mapper.toDomain(request));
        return mapper.toResponse(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or duplicate email", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
        return mapper.toResponse(service.updateUser(id, request.fullName(), request.email(), request.phone()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public void delete(@PathVariable String id) {
        service.deleteUser(id);
    }
}
