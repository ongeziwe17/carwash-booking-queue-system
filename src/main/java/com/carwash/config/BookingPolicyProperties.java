package com.carwash.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "carwash.policy.booking")
public record BookingPolicyProperties(
        @Min(1) int maxActiveBookingsPerSlot,
        @NotNull Duration cancellationWindow
) {
    public BookingPolicyProperties {
        if (cancellationWindow != null && cancellationWindow.isNegative()) {
            throw new IllegalArgumentException("Booking cancellation window must not be negative");
        }
    }
}
