package com.carwash.api;

import com.carwash.api.dto.*;
import com.carwash.api.mapper.UserMapper;
import com.carwash.service.UserManagementService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserManagementService users; private final UserMapper mapper;
    public AdminUserController(UserManagementService users, UserMapper mapper){this.users=users;this.mapper=mapper;}

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public UserResponse assignRole(@PathVariable String userId, @Valid @RequestBody AssignRoleRequest request) {
        return mapper.toResponse(users.assignRole(userId, request.roleName()));
    }
}
