package com.carwash.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "carwash.policy.queue")
public record QueuePolicyProperties(
        @NotNull Duration defaultServiceDuration
) {
    public QueuePolicyProperties {
        if (defaultServiceDuration != null
                && (defaultServiceDuration.isZero() || defaultServiceDuration.isNegative())) {
            throw new IllegalArgumentException("Queue default service duration must be positive");
        }
    }
}
