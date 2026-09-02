package com.carwash.bootstrap;

import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.bootstrap.RuntimePolicyConfig;
import com.carwash.notification.application.NotificationPolicyProperties;
import com.carwash.queue.application.QueuePolicyProperties;
import com.carwash.recommendation.application.RecommendationProperties;
import com.carwash.shared.config.RuntimeProperties;

import com.carwash.booking.api.dto.CreateBookingRequest;
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
import java.math.BigDecimal;
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
                    "carwash.policy.notification.inbox-default-page-size=20",
                    "carwash.policy.notification.inbox-maximum-page-size=100",
                    "carwash.policy.queue.default-service-duration=PT10M",
                    "carwash.recommendation.weights.distance=0.25",
                    "carwash.recommendation.weights.queue-wait=0.25",
                    "carwash.recommendation.weights.total-time=0.25",
                    "carwash.recommendation.weights.price=0.25",
                    "carwash.recommendation.max-radius-km=50.00",
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
                "carwash.policy.notification.inbox-default-page-size=15",
                "carwash.policy.notification.inbox-maximum-page-size=75",
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
            assertEquals(15, context.getBean(NotificationPolicyProperties.class).inboxDefaultPageSize());
            assertEquals(75, context.getBean(NotificationPolicyProperties.class).inboxMaximumPageSize());
            assertEquals(Duration.ofMinutes(15), context.getBean(QueuePolicyProperties.class).defaultServiceDuration());
            assertEquals(ZoneId.of("Africa/Johannesburg"), context.getBean(RuntimeProperties.class).timeZone());
            assertEquals(ZoneId.of("Africa/Johannesburg"), context.getBean(Clock.class).getZone());
            assertEquals(new BigDecimal("0.25"),
                    context.getBean(RecommendationProperties.class).weights().distance());
            assertEquals(new BigDecimal("50.00"),
                    context.getBean(RecommendationProperties.class).maxRadiusKm());
        });
    }

    @Test
    void recommendationOverridesBindAsDecimalSafeTypedProperties() {
        contextRunner.withPropertyValues(
                "carwash.recommendation.weights.distance=0.10",
                "carwash.recommendation.weights.queue-wait=0.20",
                "carwash.recommendation.weights.total-time=0.30",
                "carwash.recommendation.weights.price=0.40",
                "carwash.recommendation.max-radius-km=125.50"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            RecommendationProperties recommendations = context.getBean(RecommendationProperties.class);
            assertEquals(new BigDecimal("0.10"), recommendations.weights().distance());
            assertEquals(new BigDecimal("0.20"), recommendations.weights().queueWait());
            assertEquals(new BigDecimal("0.30"), recommendations.weights().totalTime());
            assertEquals(new BigDecimal("0.40"), recommendations.weights().price());
            assertEquals(new BigDecimal("125.50"), recommendations.maxRadiusKm());
        });
    }

    @Test
    void recommendationWeightsRetainPrecisionBeyondResponseScale() {
        contextRunner.withPropertyValues(
                "carwash.recommendation.weights.distance=0.1666666",
                "carwash.recommendation.weights.queue-wait=0.1666666",
                "carwash.recommendation.weights.total-time=0.1666666",
                "carwash.recommendation.weights.price=0.5000002"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            RecommendationProperties.Weights weights = context.getBean(RecommendationProperties.class).weights();
            assertEquals(new BigDecimal("0.1666666"), weights.distance());
            assertEquals(new BigDecimal("0.1666666"), weights.queueWait());
            assertEquals(new BigDecimal("0.1666666"), weights.totalTime());
            assertEquals(new BigDecimal("0.5000002"), weights.price());
        });
    }

    @Test
    void slotIntervalMayBeAnyPositiveWholeMinuteDuration() {
        contextRunner.withPropertyValues("carwash.policy.booking.slot-interval=PT45M")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(Duration.ofMinutes(45),
                            context.getBean(BookingPolicyProperties.class).slotInterval());
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
                            "booking-1", "user-1", "vehicle-1", "branch-1", "offering-1",
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
    void zeroNotificationInboxDefaultFailsStartupValidation() {
        assertInvalid("carwash.policy.notification.inbox-default-page-size=0", "inboxDefaultPageSize");
    }

    @Test
    void notificationInboxDefaultCannotExceedMaximum() {
        assertInvalid("carwash.policy.notification.inbox-default-page-size=101",
                "default inbox page size");
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

    @Test
    void recommendationWeightOutsideUnitRangeFailsStartupValidation() {
        contextRunner.withPropertyValues(
                "carwash.recommendation.weights.distance=1.01",
                "carwash.recommendation.weights.queue-wait=-0.51"
        ).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure);
            String messages = failureMessages(failure).toLowerCase();
            assertTrue(messages.contains("distance") || messages.contains("queuewait"),
                    () -> "Expected invalid recommendation weight field but was: " + messages);
        });
    }

    @Test
    void recommendationWeightsMustSumExactlyToOne() {
        assertInvalid("carwash.recommendation.weights.price=0.24", "sum exactly to 1");
    }

    @Test
    void nonPositiveRecommendationRadiusFailsStartupValidation() {
        assertInvalid("carwash.recommendation.max-radius-km=0", "maximum radius");
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
