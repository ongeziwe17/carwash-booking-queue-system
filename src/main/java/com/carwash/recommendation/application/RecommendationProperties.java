package com.carwash.recommendation.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "carwash.recommendation")
public record RecommendationProperties(
        @NotNull @Valid Weights weights,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("20000.0") BigDecimal maxRadiusKm
) {

    public static final BigDecimal REQUIRED_WEIGHT_TOTAL = BigDecimal.ONE;

    @ConstructorBinding
    public RecommendationProperties {
        if (weights != null && weights.total().compareTo(REQUIRED_WEIGHT_TOTAL) != 0) {
            throw new IllegalArgumentException("Recommendation weights must sum exactly to 1");
        }
        if (maxRadiusKm != null && maxRadiusKm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Recommendation maximum radius must be positive");
        }
    }

    public BigDecimal weight(RecommendationMetric metric) {
        return switch (metric) {
            case DISTANCE -> weights.distance();
            case QUEUE_WAIT -> weights.queueWait();
            case TOTAL_TIME -> weights.totalTime();
            case PRICE -> weights.price();
        };
    }

    public record Weights(
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal distance,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal queueWait,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal totalTime,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal price
    ) {
        BigDecimal total() {
            if (distance == null || queueWait == null || totalTime == null || price == null) {
                return BigDecimal.ZERO;
            }
            return distance.add(queueWait).add(totalTime).add(price);
        }
    }
}
