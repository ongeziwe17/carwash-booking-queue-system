package com.carwash.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimePolicyConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RuntimePolicyConfig.class)
            .withPropertyValues(
                    "carwash.policy.booking.max-active-bookings-per-slot=1",
                    "carwash.policy.booking.cancellation-window=PT0S",
                    "carwash.policy.notification.recent-limit=10",
                    "carwash.policy.queue.default-service-duration=PT10M",
                    "carwash.runtime.time-zone=UTC"
            );

    @Test
    void validConfigurationBindsTypedPoliciesAndConfiguredClockZone() {
        contextRunner.withPropertyValues(
                "carwash.policy.booking.max-active-bookings-per-slot=2",
                "carwash.policy.booking.cancellation-window=PT2H",
                "carwash.policy.notification.recent-limit=3",
                "carwash.policy.queue.default-service-duration=PT15M",
                "carwash.runtime.time-zone=Africa/Johannesburg"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(2, context.getBean(BookingPolicyProperties.class).maxActiveBookingsPerSlot());
            assertEquals(Duration.ofHours(2), context.getBean(BookingPolicyProperties.class).cancellationWindow());
            assertEquals(3, context.getBean(NotificationPolicyProperties.class).recentLimit());
            assertEquals(Duration.ofMinutes(15), context.getBean(QueuePolicyProperties.class).defaultServiceDuration());
            assertEquals(ZoneId.of("Africa/Johannesburg"), context.getBean(RuntimeProperties.class).timeZone());
            assertEquals(ZoneId.of("Africa/Johannesburg"), context.getBean(Clock.class).getZone());
        });
    }

    @Test
    void zeroBookingCapacityFailsStartupValidation() {
        assertInvalid("carwash.policy.booking.max-active-bookings-per-slot=0", "maxActiveBookingsPerSlot");
    }

    @Test
    void negativeBookingCapacityFailsStartupValidation() {
        assertInvalid("carwash.policy.booking.max-active-bookings-per-slot=-1", "maxActiveBookingsPerSlot");
    }

    @Test
    void negativeCancellationWindowFailsStartupValidation() {
        assertInvalid("carwash.policy.booking.cancellation-window=-PT1S", "cancellation window");
    }

    @Test
    void zeroNotificationRecentLimitFailsStartupValidation() {
        assertInvalid("carwash.policy.notification.recent-limit=0", "recentLimit");
    }

    @Test
    void invalidTimeZoneFailsStartupBinding() {
        assertInvalid("carwash.runtime.time-zone=Not/A-TimeZone", "timeZone");
    }

    @Test
    void zeroQueueFallbackDurationFailsStartupValidation() {
        assertInvalid("carwash.policy.queue.default-service-duration=PT0S", "default service duration");
    }

    @Test
    void negativeQueueFallbackDurationFailsStartupValidation() {
        assertInvalid("carwash.policy.queue.default-service-duration=-PT1M", "default service duration");
    }

    private void assertInvalid(String property, String expectedCause) {
        contextRunner.withPropertyValues(property).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure);
            assertTrue(failureMessages(failure).toLowerCase().contains(expectedCause.toLowerCase()),
                    () -> "Expected startup failure to mention '" + expectedCause + "' but was: "
                            + failureMessages(failure));
        });
    }

    private String failureMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            messages.append(current.getClass().getSimpleName()).append(": ")
                    .append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return messages.toString();
    }
}
