package com.carwash.marketplace.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public final class CarWashBranch {

    private final String branchId;
    private final String businessId;
    private final String branchName;
    private final String addressLine1;
    private final String addressLine2;
    private final String city;
    private final String province;
    private final String postalCode;
    private final String countryCode;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final String timezone;
    private final BranchStatus status;
    private final boolean publicDiscoveryEnabled;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public CarWashBranch(
            String branchId,
            String businessId,
            String branchName,
            String addressLine1,
            String addressLine2,
            String city,
            String province,
            String postalCode,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String timezone,
            BranchStatus status,
            boolean publicDiscoveryEnabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.branchId = branchId;
        this.businessId = businessId;
        this.branchName = branchName;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.province = province;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timezone = timezone;
        this.status = Objects.requireNonNull(status, "Branch status is required");
        this.publicDiscoveryEnabled = publicDiscoveryEnabled;
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Update time is required");
    }

    public String getBranchId() {
        return branchId;
    }

    public String getBusinessId() {
        return businessId;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getProvince() {
        return province;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getTimezone() {
        return timezone;
    }

    public BranchStatus getStatus() {
        return status;
    }

    public boolean isPublicDiscoveryEnabled() {
        return publicDiscoveryEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return status == BranchStatus.ACTIVE;
    }

    public CarWashBranch updateDetails(
            String branchName,
            String addressLine1,
            String addressLine2,
            String city,
            String province,
            String postalCode,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String timezone,
            boolean publicDiscoveryEnabled,
            LocalDateTime changedAt
    ) {
        return new CarWashBranch(
                branchId,
                businessId,
                branchName,
                addressLine1,
                addressLine2,
                city,
                province,
                postalCode,
                countryCode,
                latitude,
                longitude,
                timezone,
                status,
                publicDiscoveryEnabled,
                createdAt,
                changedAt
        );
    }

    public CarWashBranch activate(LocalDateTime changedAt) {
        return withStatus(BranchStatus.ACTIVE, changedAt);
    }

    public CarWashBranch deactivate(LocalDateTime changedAt) {
        return withStatus(BranchStatus.INACTIVE, changedAt);
    }

    private CarWashBranch withStatus(BranchStatus newStatus, LocalDateTime changedAt) {
        return new CarWashBranch(
                branchId,
                businessId,
                branchName,
                addressLine1,
                addressLine2,
                city,
                province,
                postalCode,
                countryCode,
                latitude,
                longitude,
                timezone,
                newStatus,
                publicDiscoveryEnabled,
                createdAt,
                changedAt
        );
    }
}
