package com.carwash.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(PasswordSecurityProperties.class)
public class CredentialSecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder(PasswordSecurityProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }
}
