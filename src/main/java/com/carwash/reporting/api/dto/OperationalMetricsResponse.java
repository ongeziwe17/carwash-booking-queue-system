package com.carwash.reporting.api.dto;

import com.carwash.reporting.application.OperationalMetrics;
import java.math.BigDecimal;
import java.util.Map;

public record OperationalMetricsResponse(long bookingVolume, Map<String,Long> bookingStatus,
        long cancellations, BigDecimal cancellationRate, long queueJoined, long serviceStarts,
        long queueCompletions, DurationResponse actualWait, DurationResponse actualServiceDuration,
        CapacityResponse scheduledCapacity) {
    public record DurationResponse(long sampleCount, BigDecimal averageSeconds, Long maximumSeconds) { }
    public record CapacityResponse(boolean available, BigDecimal bookedMinutes, BigDecimal availableMinutes,
                                   BigDecimal utilizationPercent, String unavailableReason) { }
    public static OperationalMetricsResponse from(OperationalMetrics value) {
        var wait=value.actualWait();var duration=value.serviceDuration();var capacity=value.capacity();
        return new OperationalMetricsResponse(value.bookingVolume(),value.bookingStatus(),value.cancellations(),
                value.cancellationRate(),value.queueJoined(),value.serviceStarts(),value.queueCompletions(),
                new DurationResponse(wait.sampleCount(),wait.averageSeconds(),wait.maximumSeconds()),
                new DurationResponse(duration.sampleCount(),duration.averageSeconds(),duration.maximumSeconds()),
                new CapacityResponse(capacity.available(),capacity.bookedMinutes(),capacity.availableMinutes(),
                        capacity.utilizationPercent(),capacity.unavailableReason()));
    }
}
