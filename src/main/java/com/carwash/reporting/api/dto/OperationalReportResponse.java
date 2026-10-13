package com.carwash.reporting.api.dto;

import com.carwash.reporting.application.OperationalReport;
import com.carwash.shared.api.PageResponse;
import java.time.LocalDate;

public final class OperationalReportResponse {
    private OperationalReportResponse() { }
    public record Summary(String scopeType, String scopeId, String timezoneSemantics, LocalDate startDate,
                          LocalDate endDate, OperationalMetricsResponse metrics) {
        public static Summary from(OperationalReport report) {
            return new Summary(report.scopeType(),report.scopeId(),report.timezoneSemantics(),
                    report.startDate(),report.endDate(),OperationalMetricsResponse.from(report.summary()));
        }
    }
    public record Page<T>(String scopeType, String scopeId, String timezoneSemantics,
                          LocalDate startDate, LocalDate endDate, PageResponse<T> results) {
        public static <T> Page<T> of(OperationalReport report, PageResponse<T> results) {
            return new Page<>(report.scopeType(),report.scopeId(),report.timezoneSemantics(),
                    report.startDate(),report.endDate(),results);
        }
    }
    public record Daily(LocalDate date, OperationalMetricsResponse metrics) {
        public static Daily from(OperationalReport.Daily value) {
            return new Daily(value.date(),OperationalMetricsResponse.from(value.metrics()));
        }
    }
    public record Branch(String branchId, String timezone, OperationalMetricsResponse metrics) {
        public static Branch from(OperationalReport.Branch value) {
            return new Branch(value.branchId(),value.timezone(),OperationalMetricsResponse.from(value.metrics()));
        }
    }
    public record Offering(String offeringId, String serviceId, long bookingVolume) {
        public static Offering from(OperationalReport.Offering value) {
            return new Offering(value.offeringId(),value.serviceId(),value.bookingVolume());
        }
    }
    public record PeakHour(int hour, long bookingVolume, long queueCompletions) {
        public static PeakHour from(OperationalReport.PeakHour value) {
            return new PeakHour(value.hour(),value.bookingVolume(),value.queueCompletions());
        }
    }
}
