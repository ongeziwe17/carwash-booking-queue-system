package com.carwash.discovery.application;

import com.carwash.discovery.domain.GeoCoordinate;
import com.carwash.shared.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.Instant;

public record NearbyBranchSearchCriteria(
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal radiusKm,
        String serviceId,
        Instant openAt,
        NearbyBranchSort sort
) {
    public static final BigDecimal MAX_RADIUS_KM = new BigDecimal("20000.00");
    public static final int MAX_ID_LENGTH = 64;

    public NearbyBranchSearchCriteria {
        validateCoordinate(latitude, -90.0d, 90.0d, "Latitude");
        validateCoordinate(longitude, -180.0d, 180.0d, "Longitude");
        if (radiusKm != null
                && (radiusKm.compareTo(BigDecimal.ZERO) <= 0 || radiusKm.compareTo(MAX_RADIUS_KM) > 0)) {
            throw new BusinessRuleViolationException(
                    "Radius must be greater than zero and no more than " + MAX_RADIUS_KM.toPlainString() + " km");
        }
        if (serviceId != null) {
            serviceId = serviceId.trim();
            if (serviceId.isBlank()) {
                throw new BusinessRuleViolationException("Service ID must not be blank");
            }
            if (serviceId.length() > MAX_ID_LENGTH) {
                throw new BusinessRuleViolationException(
                        "Service ID must not exceed " + MAX_ID_LENGTH + " characters");
            }
        }
        sort = sort == null ? NearbyBranchSort.BRANCH_ID : sort;
    }

    public GeoCoordinate origin() {
        return new GeoCoordinate(latitude.doubleValue(), longitude.doubleValue());
    }

    private static void validateCoordinate(BigDecimal value, double minimum, double maximum, String field) {
        if (value == null) {
            throw new BusinessRuleViolationException(field + " is required");
        }
        double numeric = value.doubleValue();
        if (!Double.isFinite(numeric) || numeric < minimum || numeric > maximum) {
            throw new BusinessRuleViolationException(field + " is outside the valid range");
        }
    }
}
