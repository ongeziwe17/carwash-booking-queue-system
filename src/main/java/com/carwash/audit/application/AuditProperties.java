package com.carwash.audit.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "carwash.audit")
public record AuditProperties(
        @NotNull Duration retentionPeriod,
        @Min(1) int defaultPageSize,
        @Min(1) int maximumPageSize,
        @NotNull Duration maximumQueryRange,
        @Min(1) @Max(32) int metadataMaximumKeys,
        @Min(1) @Max(128) int metadataMaximumKeyLength,
        @Min(1) @Max(1024) int metadataMaximumValueLength,
        @Min(64) @Max(16384) int metadataMaximumTotalSize
) {
    @ConstructorBinding
    public AuditProperties {
        if (retentionPeriod != null && (retentionPeriod.isZero() || retentionPeriod.isNegative())) {
            throw new IllegalArgumentException("Audit retention period must be positive");
        }
        if (maximumQueryRange != null && (maximumQueryRange.isZero() || maximumQueryRange.isNegative())) {
            throw new IllegalArgumentException("Audit maximum query range must be positive");
        }
        if (defaultPageSize > maximumPageSize) {
            throw new IllegalArgumentException("Audit default page size cannot exceed the maximum");
        }
    }
}
