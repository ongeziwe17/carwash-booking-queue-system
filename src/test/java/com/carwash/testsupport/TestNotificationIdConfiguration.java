package com.carwash.testsupport;

import com.carwash.notification.application.NotificationIdGenerator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestNotificationIdConfiguration {

    @Bean
    @Primary
    public NotificationIdGenerator testNotificationIdGenerator(
            DeterministicTestNotificationIdGenerator generator
    ) {
        return generator;
    }

    @Bean
    public DeterministicTestNotificationIdGenerator deterministicTestNotificationIdGenerator() {
        return new DeterministicTestNotificationIdGenerator();
    }
}
