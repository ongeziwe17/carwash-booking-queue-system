package com.carwash.api;

import com.carwash.api.dto.*;
import com.carwash.api.mapper.UserMapper;
import com.carwash.service.UserManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserManagementService users; private final UserMapper mapper;
    public AdminUserController(UserManagementService users, UserMapper mapper){this.users=users;this.mapper=mapper;}

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public UserResponse assignRole(@PathVariable @NotBlank @Size(max = 64) String userId, @Valid @RequestBody AssignRoleRequest request) {
        return mapper.toResponse(users.assignRole(userId, request.roleName()));
    }
}
