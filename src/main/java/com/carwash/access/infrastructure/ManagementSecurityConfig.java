package com.carwash.access.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** Dedicated, database-independent scrape identity; customer bearer tokens are never accepted. */
@Configuration(proxyBeanMethods = false)
public class ManagementSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain managementSecurity(HttpSecurity http,
            @Value("${carwash.observability.scrape.username:}") String username,
            @Value("${carwash.observability.scrape.password-hash:}") String passwordHash) throws Exception {
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
        if (!username.isBlank() || !passwordHash.isBlank()) {
            if (username.isBlank() || !passwordHash.matches("\\{bcrypt}\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")) {
                throw new IllegalStateException("Scrape authentication requires a username and a bcrypt hash");
            }
            users.createUser(User.withUsername(username).password(passwordHash).roles("SCRAPE").build());
        }
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());
        return http.securityMatcher("/actuator", "/actuator/**")
                .authenticationManager(new ProviderManager(provider))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(b -> b.realmName("telemetry")
                        .authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").hasRole("SCRAPE")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.accessDeniedHandler((req, res, ex) -> res.sendError(403)))
                .build();
    }
}
