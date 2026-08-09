package com.carwash.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "carwash.policy.notification")
public record NotificationPolicyProperties(
        @Min(1) int recentLimit
) {
}
