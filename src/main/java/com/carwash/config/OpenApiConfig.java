package com.carwash.config;

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
    public OpenAPI carwashOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .info(new Info().title("Car Wash Booking and Queue Management API").version("1.0")
                        .description("Register and login publicly, then use a bearer token for protected APIs. "
                                + "JWT authentication, RBAC, and ownership authorization are implemented. "
                                + "Marketplace tenant isolation is not yet implemented, so elevated operational "
                                + "access is currently global."));
    }
}
