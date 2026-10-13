package com.carwash.reporting.application;

import java.math.BigDecimal;
import java.util.Map;

/** Counts and duration units belong to the operational reporting contract, independent of HTTP. */
public record OperationalMetrics(long bookingVolume, Map<String,Long> bookingStatus,
                                 long cancellations, BigDecimal cancellationRate,
                                 long queueJoined, long serviceStarts, long queueCompletions,
                                 DurationSamples actualWait, DurationSamples serviceDuration,
                                 Capacity capacity) {
    public OperationalMetrics { bookingStatus = Map.copyOf(bookingStatus); }
    public record DurationSamples(long sampleCount, BigDecimal averageSeconds, Long maximumSeconds) { }
    public record Capacity(boolean available, BigDecimal bookedMinutes, BigDecimal availableMinutes,
                           BigDecimal utilizationPercent, String unavailableReason) { }
}
