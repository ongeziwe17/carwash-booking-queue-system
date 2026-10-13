package com.carwash.bootstrap;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/** Fail closed before bean creation if a durable deployment is misconfigured. */
public final class DeploymentProfileGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override public void initialize(ConfigurableApplicationContext context) {
        validate(context.getEnvironment());
    }

    static void validate(ConfigurableEnvironment environment) {
        boolean staging = environment.matchesProfiles("staging");
        boolean production = environment.matchesProfiles("production");
        if (!staging && !production) return;
        if (staging && production) throw invalid();
        if (!environment.matchesProfiles("postgres")) throw invalid();
        try {
            String url = environment.getProperty("spring.datasource.url", "");
            String username = environment.getProperty("spring.datasource.username", "");
            String password = environment.getProperty("spring.datasource.password", "");
            String exclude = environment.getProperty("spring.autoconfigure.exclude", "");
            if (!url.startsWith("jdbc:postgresql://") || username.isBlank() || password.isBlank()
                    || !exclude.isBlank()
                    || !environment.getProperty("spring.flyway.enabled", Boolean.class, false)
                    || !environment.getProperty("spring.flyway.clean-disabled", Boolean.class, false)
                    || environment.getProperty("carwash.security.bootstrap-admin.enabled", Boolean.class, false)
                    || environment.getProperty("springdoc.api-docs.enabled", Boolean.class, true)
                    || environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true)) {
                throw invalid();
            }
        } catch (RuntimeException invalidConfiguration) {
            throw invalid();
        }
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Invalid staging/production deployment configuration");
    }
}
