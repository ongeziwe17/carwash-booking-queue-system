package com.carwash.identity.api.mapper;

import com.carwash.identity.api.dto.UserResponse;
import com.carwash.identity.domain.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

    public UserResponse toListResponse(com.carwash.identity.application.UserListSnapshot user) {
        return new UserResponse(user.userId(), user.fullName(), user.email(), user.phone(),
                user.accountStatus(), user.createdAt(), user.lastLoginAt(), user.roleName());
    }

    public UserResponse toResponse(User user) {
        String roleName = user.getRole() == null ? null : user.getRole().getRoleName();
        return new UserResponse(user.getUserId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getAccountStatus(), user.getCreatedAt(), user.getLastLoginAt(), roleName);
    }

    public List<UserResponse> toResponseList(List<User> users) {
        return users.stream().map(this::toResponse).toList();
    }
}
