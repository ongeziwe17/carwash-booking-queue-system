package com.carwash.booking.application;

import com.carwash.booking.domain.Booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalTime;

@Validated
@ConfigurationProperties(prefix = "carwash.policy.booking")
public record BookingPolicyProperties(
        @Min(1) int maxActiveBookingsPerSlot,
        @NotNull Duration cancellationWindow,
        @NotNull LocalTime operatingStart,
        @NotNull LocalTime operatingEnd,
        @NotNull Duration slotInterval
) {

    public static final LocalTime DEFAULT_OPERATING_START = LocalTime.of(8, 0);
    public static final LocalTime DEFAULT_OPERATING_END = LocalTime.of(17, 0);
    public static final Duration DEFAULT_SLOT_INTERVAL = Duration.ofMinutes(30);

    @ConstructorBinding
    public BookingPolicyProperties {
        if (cancellationWindow != null && cancellationWindow.isNegative()) {
            throw new IllegalArgumentException("Booking cancellation window must not be negative");
        }
        if (operatingStart != null && operatingEnd != null && !operatingStart.isBefore(operatingEnd)) {
            throw new IllegalArgumentException("Booking operating start must be before operating end");
        }
        if (slotInterval != null
                && (slotInterval.compareTo(Duration.ofMinutes(1)) < 0
                || slotInterval.getNano() != 0
                || slotInterval.getSeconds() % 60 != 0)) {
            throw new IllegalArgumentException("Booking slot interval must be a positive whole-minute duration");
        }
    }

    public BookingPolicyProperties(int maxActiveBookingsPerSlot, Duration cancellationWindow) {
        this(maxActiveBookingsPerSlot, cancellationWindow,
                DEFAULT_OPERATING_START, DEFAULT_OPERATING_END, DEFAULT_SLOT_INTERVAL);
    }
}
