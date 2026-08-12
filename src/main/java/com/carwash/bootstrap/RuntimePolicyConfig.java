package com.carwash.bootstrap;

import com.carwash.shared.config.RuntimeProperties;
import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.queue.application.QueuePolicyProperties;
import com.carwash.notification.application.NotificationPolicyProperties;

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
