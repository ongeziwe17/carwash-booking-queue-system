package com.carwash.api.mapper;

import com.carwash.api.dto.UserResponse;
import com.carwash.domain.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        String roleName = user.getRole() == null ? null : user.getRole().getRoleName();
        return new UserResponse(user.getUserId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getAccountStatus(), user.getCreatedAt(), user.getLastLoginAt(), roleName);
    }

    public List<UserResponse> toResponseList(List<User> users) {
        return users.stream().map(this::toResponse).toList();
    }
}
