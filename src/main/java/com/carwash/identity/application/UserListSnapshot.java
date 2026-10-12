package com.carwash.identity.application;

import com.carwash.identity.domain.User;
import com.carwash.identity.domain.AccountStatus;
import java.time.LocalDateTime;

/** Immutable list projection captured before releasing the read snapshot; no credentials or aggregates. */
public record UserListSnapshot(String userId, String fullName, String email, String phone,
                               AccountStatus accountStatus, LocalDateTime createdAt, LocalDateTime lastLoginAt, String roleName) {
    public static UserListSnapshot from(User user) {
        return new UserListSnapshot(user.getUserId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getAccountStatus(), user.getCreatedAt(), user.getLastLoginAt(),
                user.getRole() == null ? null : user.getRole().getRoleName());
    }
}
