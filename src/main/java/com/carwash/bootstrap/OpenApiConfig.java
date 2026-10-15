package com.carwash.bootstrap;

import com.carwash.booking.domain.Booking;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hlambaqOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .info(new Info().title("HlambaQ API").version("1.0")
                        .description("HlambaQ is a car wash marketplace, booking and queue management platform. "
                                + "It connects customers with car wash businesses and supports tenant-scoped "
                                + "branch operations and platform administration. Register and login publicly; "
                                + "protected APIs require a bearer JWT and enforce role, ownership and tenant scope."));
    }
}
