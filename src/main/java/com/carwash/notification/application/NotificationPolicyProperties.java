package com.carwash.notification.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "carwash.policy.notification")
public record NotificationPolicyProperties(
        @Min(1) @Max(500) int recentLimit,
        @Min(1) @Max(500) int inboxDefaultPageSize,
        @Min(1) @Max(500) int inboxMaximumPageSize
) {
    public NotificationPolicyProperties {
        if (inboxDefaultPageSize > inboxMaximumPageSize) {
            throw new IllegalArgumentException(
                    "Notification default inbox page size must not exceed the maximum");
        }
    }

    /** Retained for focused service tests and internal composition. */
    public NotificationPolicyProperties(int recentLimit) {
        this(recentLimit, 20, 100);
    }
}
