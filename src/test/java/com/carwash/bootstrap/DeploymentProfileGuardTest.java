package com.carwash.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeploymentProfileGuardTest {
    private static MockEnvironment valid(String profile) {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://database:5432/carwash")
                .withProperty("spring.datasource.username", "service")
                .withProperty("spring.datasource.password", "test-password")
                .withProperty("spring.autoconfigure.exclude", "")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.flyway.clean-disabled", "true")
                .withProperty("carwash.security.bootstrap-admin.enabled", "false")
                .withProperty("springdoc.api-docs.enabled", "false")
                .withProperty("springdoc.swagger-ui.enabled", "false")
                .withProperty("spring.profiles.active", profile);
    }

    @Test void productionAndStagingRequireThePostgresProfileAndDurableConfiguration() {
        for (String profile : new String[]{"staging", "production"}) {
            var env = valid(profile);
            env.setActiveProfiles(profile, "postgres");
            assertDoesNotThrow(() -> DeploymentProfileGuard.validate(env));
            env.setActiveProfiles(profile);
            assertRejected(env);
            env.setActiveProfiles(profile, "postgres");
            env.withProperty("spring.datasource.url", "jdbc:h2:mem:unsafe");
            assertRejected(env);
        }
        var local = new MockEnvironment();
        local.setActiveProfiles("test");
        assertDoesNotThrow(() -> DeploymentProfileGuard.validate(local));
    }

    @Test void missingSecretsOverridesAndUnsafeProductionFeaturesFailWithoutLoggingValues() {
        var env = valid("production");
        env.setActiveProfiles("production", "postgres");
        for (String key : new String[]{"spring.datasource.password", "spring.datasource.username"}) {
            var invalid = valid("production");
            invalid.setActiveProfiles("production", "postgres");
            invalid.withProperty(key, "");
            assertRejected(invalid);
        }
        for (String key : new String[]{"spring.autoconfigure.exclude",
                "carwash.security.bootstrap-admin.enabled", "springdoc.api-docs.enabled",
                "springdoc.swagger-ui.enabled", "spring.flyway.enabled"}) {
            var invalid = valid("production");
            invalid.setActiveProfiles("production", "postgres");
            invalid.withProperty(key, key.equals("spring.autoconfigure.exclude") ? "DataSourceAutoConfiguration"
                    : key.equals("spring.flyway.enabled") ? "false" : "true");
            assertRejected(invalid);
        }
        env.setActiveProfiles("production", "staging", "postgres");
        assertRejected(env);
    }

    private static void assertRejected(MockEnvironment environment) {
        assertEquals("Invalid staging/production deployment configuration",
                assertThrows(IllegalStateException.class, () -> DeploymentProfileGuard.validate(environment)).getMessage());
    }
}
