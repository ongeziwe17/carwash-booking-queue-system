package com.carwash.config;

import com.carwash.api.dto.CreateBookingRequest;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimePolicyConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(RuntimePolicyConfig.class)
            .withPropertyValues(
                    "carwash.policy.booking.max-active-bookings-per-slot=1",
                    "carwash.policy.booking.cancellation-window=PT0S",
                    "carwash.policy.booking.operating-start=08:00",
                    "carwash.policy.booking.operating-end=17:00",
                    "carwash.policy.booking.slot-interval=PT30M",
                    "carwash.policy.notification.recent-limit=10",
                    "carwash.policy.queue.default-service-duration=PT10M",
                    "carwash.runtime.time-zone=UTC"
            );

    @Test
    void validConfigurationBindsTypedPoliciesAndConfiguredClockZone() {
        contextRunner.withPropertyValues(
                "carwash.policy.booking.max-active-bookings-per-slot=2",
                "carwash.policy.booking.cancellation-window=PT2H",
                "carwash.policy.booking.operating-start=07:30",
                "carwash.policy.booking.operating-end=18:30",
                "carwash.policy.booking.slot-interval=PT15M",
                "carwash.policy.notification.recent-limit=3",
                "carwash.policy.queue.default-service-duration=PT15M",
                "carwash.runtime.time-zone=Africa/Johannesburg"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(2, context.getBean(BookingPolicyProperties.class).maxActiveBookingsPerSlot());
            assertEquals(Duration.ofHours(2), context.getBean(BookingPolicyProperties.class).cancellationWindow());
            assertEquals(LocalTime.of(7, 30), context.getBean(BookingPolicyProperties.class).operatingStart());
            assertEquals(LocalTime.of(18, 30), context.getBean(BookingPolicyProperties.class).operatingEnd());
            assertEquals(Duration.ofMinutes(15), context.getBean(BookingPolicyProperties.class).slotInterval());
            assertEquals(3, context.getBean(NotificationPolicyProperties.class).recentLimit());
            assertEquals(Duration.ofMinutes(15), context.getBean(QueuePolicyProperties.class).defaultServiceDuration());
            assertEquals(ZoneId.of("Africa/Johannesburg"), context.getBean(RuntimeProperties.class).timeZone());
            assertEquals(ZoneId.of("Africa/Johannesburg"), context.getBean(Clock.class).getZone());
        });
    }

    @Test
    void beanValidationFutureConstraintUsesApplicationClock() {
        contextRunner
                .withUserConfiguration(FixedClockConfiguration.class)
                .withPropertyValues("carwash.runtime.time-zone=America/New_York")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    Validator validator = context.getBean(Validator.class);

                    CreateBookingRequest futureAccordingToApplicationClock = new CreateBookingRequest(
                            "booking-1", "user-1", "vehicle-1", "service-1",
                            LocalDateTime.of(2000, 1, 1, 7, 30), null);

                    Set<String> violatedFields = validator.validate(futureAccordingToApplicationClock).stream()
                            .map(violation -> violation.getPropertyPath().toString())
                            .collect(java.util.stream.Collectors.toSet());

                    assertTrue(violatedFields.isEmpty(),
                            () -> "Expected @Future to use the fixed application clock, violations: " + violatedFields);
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
    void reversedOperatingWindowFailsStartupValidation() {
        assertInvalid("carwash.policy.booking.operating-start=18:00",
                "operating start must be before operating end");
    }

    @Test
    void equalOperatingWindowFailsStartupValidation() {
        assertInvalid("carwash.policy.booking.operating-start=17:00",
                "operating start must be before operating end");
    }

    @Test
    void zeroSlotIntervalFailsStartupValidation() {
        assertInvalid("carwash.policy.booking.slot-interval=PT0S", "slot interval");
    }

    @Test
    void negativeSlotIntervalFailsStartupValidation() {
        assertInvalid("carwash.policy.booking.slot-interval=-PT1M", "slot interval");
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

    @Configuration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2000-01-01T12:00:00Z"), ZoneId.of("America/New_York"));
        }
    }
}
