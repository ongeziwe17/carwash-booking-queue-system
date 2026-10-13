package com.carwash.feedback.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Two decimal places, HALF_UP; zero reviews have a null average. */
public record RatingSummary(long reviewCount, BigDecimal averageRating, List<Long> distribution) {
    public RatingSummary { distribution = List.copyOf(distribution); }
    public static RatingSummary from(long[] counts) {
        long total = 0, sum = 0;
        for (int i = 0; i < 5; i++) { total += counts[i]; sum += (i + 1) * counts[i]; }
        return new RatingSummary(total, total == 0 ? null : BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP),
                java.util.Arrays.stream(counts).boxed().toList());
    }
}
