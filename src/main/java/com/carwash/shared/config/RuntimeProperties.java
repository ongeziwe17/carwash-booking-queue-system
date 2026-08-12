package com.carwash.shared.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

@Validated
@ConfigurationProperties(prefix = "carwash.runtime")
public record RuntimeProperties(
        @NotNull ZoneId timeZone
) {
}
