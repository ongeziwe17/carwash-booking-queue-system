package com.carwash.config;

import com.carwash.api.error.ApiErrorCode;
import com.carwash.api.error.ApiErrorResponseFactory;
import com.carwash.enums.AccountStatus;
import com.carwash.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class ApiSecurityConfig {
    @Bean
    SecretKey jwtSigningKey(JwtSecurityProperties properties) {
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT secret must be valid Base64");
        }
        if (decoded.length < 32) throw new IllegalStateException("JWT secret must decode to at least 32 bytes");
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) { return NimbusJwtEncoder.withSecretKey(key).build(); }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, JwtSecurityProperties properties, UserRepository users) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> standard = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> activeUser = jwt -> {
            String subject = jwt.getSubject();
            boolean valid = subject != null && !subject.isBlank() && jwt.getIssuedAt() != null && jwt.getExpiresAt() != null
                    && users.findById(subject).filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE)
                    .filter(u -> {
                        try { return com.carwash.security.RoleCatalog.name(u.getRole()).name().equals(jwt.getClaimAsString("role")); }
                        catch (RuntimeException ex) { return false; }
                    }).isPresent();
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token subject is invalid", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(standard, activeUser));
        return decoder;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ObjectMapper objectMapper, ApiErrorResponseFactory errors) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(new com.carwash.security.JwtAuthorityConverter()))
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), errors.create(
                                    org.springframework.http.HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTHENTICATION_REQUIRED,
                                    "Authentication is required", request));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), errors.create(
                                    org.springframework.http.HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED,
                                    "Access denied", request));
                        }))
                .build();
    }
}
