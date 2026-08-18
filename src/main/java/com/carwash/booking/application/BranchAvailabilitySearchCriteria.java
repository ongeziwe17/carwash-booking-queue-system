package com.carwash.booking.application;

import com.carwash.discovery.domain.GeoCoordinate;
import com.carwash.shared.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.Instant;

public record BranchAvailabilitySearchCriteria(
        String serviceId,
        Instant startsAt,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal radiusKm
) {
    public static final BigDecimal MAX_RADIUS_KM = new BigDecimal("20000.00");

    public BranchAvailabilitySearchCriteria {
        serviceId = normalizeRequiredId(serviceId);
        if (startsAt == null) {
            throw new BusinessRuleViolationException("Availability start instant is required");
        }
        boolean hasLatitude = latitude != null;
        boolean hasLongitude = longitude != null;
        if (hasLatitude != hasLongitude) {
            throw new BusinessRuleViolationException("Latitude and longitude must be supplied together");
        }
        if (radiusKm != null && !hasLatitude) {
            throw new BusinessRuleViolationException("Radius requires latitude and longitude");
        }
        if (hasLatitude) {
            validateCoordinate(latitude, -90.0d, 90.0d, "Latitude");
            validateCoordinate(longitude, -180.0d, 180.0d, "Longitude");
        }
        if (radiusKm != null
                && (radiusKm.compareTo(BigDecimal.ZERO) <= 0 || radiusKm.compareTo(MAX_RADIUS_KM) > 0)) {
            throw new BusinessRuleViolationException(
                    "Radius must be greater than zero and no more than " + MAX_RADIUS_KM.toPlainString() + " km");
        }
    }

    public boolean hasOrigin() {
        return latitude != null;
    }

    public GeoCoordinate origin() {
        if (!hasOrigin()) {
            throw new IllegalStateException("Availability search has no origin coordinate");
        }
        return new GeoCoordinate(latitude.doubleValue(), longitude.doubleValue());
    }

    private static String normalizeRequiredId(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessRuleViolationException("Service ID is required");
        }
        if (normalized.length() > 64) {
            throw new BusinessRuleViolationException("Service ID must not exceed 64 characters");
        }
        return normalized;
    }

    private static void validateCoordinate(BigDecimal value, double minimum, double maximum, String field) {
        double numeric = value.doubleValue();
        if (!Double.isFinite(numeric) || numeric < minimum || numeric > maximum) {
            throw new BusinessRuleViolationException(field + " is outside the valid range");
        }
    }
}
