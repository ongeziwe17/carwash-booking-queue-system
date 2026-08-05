package com.carwash.service.command;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Registration data kept only for the duration of user creation. */
public record CreateUserCommand(
        String userId,
        String fullName,
        String email,
        String phone,
        @JsonIgnore String rawPassword
) {
    @Override
    public String toString() {
        return "CreateUserCommand[userId=" + userId + ", fullName=" + fullName + ", email=" + email
                + ", phone=" + phone + ", rawPassword=[REDACTED]]";
    }
}
