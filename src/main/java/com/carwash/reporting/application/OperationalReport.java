package com.carwash.reporting.application;

import java.time.LocalDate;
import java.util.List;

public record OperationalReport(String scopeType, String scopeId, String timezoneSemantics,
                                LocalDate startDate, LocalDate endDate, OperationalMetrics summary,
                                List<Daily> daily, List<Branch> branches, List<Offering> offerings,
                                List<PeakHour> peakHours) {
    public OperationalReport {
        daily=List.copyOf(daily); branches=List.copyOf(branches);
        offerings=List.copyOf(offerings); peakHours=List.copyOf(peakHours);
    }
    public record Daily(LocalDate date, OperationalMetrics metrics) { }
    public record Branch(String branchId, String timezone, OperationalMetrics metrics) { }
    public record Offering(String offeringId, String serviceId, long bookingVolume) { }
    public record PeakHour(int hour, long bookingVolume, long queueCompletions) { }
}
