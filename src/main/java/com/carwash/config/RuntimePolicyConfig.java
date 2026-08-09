package com.carwash.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({
        BookingPolicyProperties.class,
        NotificationPolicyProperties.class,
        QueuePolicyProperties.class,
        RuntimeProperties.class
})
public class RuntimePolicyConfig {

    @Bean
    Clock applicationClock(RuntimeProperties properties) {
        return Clock.system(properties.timeZone());
    }

    @Bean
    ValidationConfigurationCustomizer applicationClockValidationCustomizer(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
