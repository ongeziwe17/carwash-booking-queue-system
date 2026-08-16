package com.carwash.catalog.domain;

import com.carwash.shared.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public final class ServiceOffering {

    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_PRICE_INTEGER_DIGITS = 10;
    public static final int MAX_PRICE_FRACTION_DIGITS = 2;
    public static final int MAX_ESTIMATED_DURATION_MIN = 1440;
    public static final int MAX_CONCURRENT_CAPACITY = 1000;

    private final String offeringId;
    private final String branchId;
    private final String serviceId;
    private final BigDecimal price;
    private final int estimatedDurationMin;
    private final int concurrentCapacity;
    private final ServiceOfferingStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public ServiceOffering(
            String offeringId,
            String branchId,
            String serviceId,
            BigDecimal price,
            int estimatedDurationMin,
            int concurrentCapacity,
            ServiceOfferingStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.offeringId = requireId(offeringId, "Offering ID");
        this.branchId = requireId(branchId, "Branch ID");
        this.serviceId = requireId(serviceId, "Service ID");
        this.price = requirePrice(price);
        this.estimatedDurationMin = requireRange(
                estimatedDurationMin, 1, MAX_ESTIMATED_DURATION_MIN, "Estimated duration");
        this.concurrentCapacity = requireRange(
                concurrentCapacity, 1, MAX_CONCURRENT_CAPACITY, "Concurrent capacity");
        this.status = Objects.requireNonNull(status, "Offering status is required");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Update time is required");
    }

    public String getOfferingId() {
        return offeringId;
    }

    public String getBranchId() {
        return branchId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getEstimatedDurationMin() {
        return estimatedDurationMin;
    }

    public int getConcurrentCapacity() {
        return concurrentCapacity;
    }

    public ServiceOfferingStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return status == ServiceOfferingStatus.ACTIVE;
    }

    public ServiceOffering updateTerms(
            BigDecimal updatedPrice,
            int updatedEstimatedDurationMin,
            int updatedConcurrentCapacity,
            LocalDateTime changedAt
    ) {
        return new ServiceOffering(
                offeringId,
                branchId,
                serviceId,
                updatedPrice,
                updatedEstimatedDurationMin,
                updatedConcurrentCapacity,
                status,
                createdAt,
                changedAt
        );
    }

    public ServiceOffering activate(LocalDateTime changedAt) {
        return withStatus(ServiceOfferingStatus.ACTIVE, changedAt);
    }

    public ServiceOffering deactivate(LocalDateTime changedAt) {
        return withStatus(ServiceOfferingStatus.INACTIVE, changedAt);
    }

    private ServiceOffering withStatus(ServiceOfferingStatus newStatus, LocalDateTime changedAt) {
        return new ServiceOffering(
                offeringId,
                branchId,
                serviceId,
                price,
                estimatedDurationMin,
                concurrentCapacity,
                newStatus,
                createdAt,
                changedAt
        );
    }

    private static String requireId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(field + " must not be blank");
        }
        if (value.length() > MAX_ID_LENGTH) {
            throw new BusinessRuleViolationException(field + " must not exceed " + MAX_ID_LENGTH + " characters");
        }
        return value;
    }

    private static BigDecimal requirePrice(BigDecimal value) {
        if (value == null) {
            throw new BusinessRuleViolationException("Offering price is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleViolationException("Offering price must be zero or positive");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        int fractionDigits = Math.max(0, normalized.scale());
        int integerDigits = Math.max(0, normalized.precision() - normalized.scale());
        if (fractionDigits > MAX_PRICE_FRACTION_DIGITS || integerDigits > MAX_PRICE_INTEGER_DIGITS) {
            throw new BusinessRuleViolationException(
                    "Offering price must use at most 10 integer digits and 2 decimal places");
        }
        return value;
    }

    private static int requireRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new BusinessRuleViolationException(
                    field + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
