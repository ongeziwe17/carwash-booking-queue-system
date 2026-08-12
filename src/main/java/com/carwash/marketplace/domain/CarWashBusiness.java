package com.carwash.marketplace.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public final class CarWashBusiness {

    private final String businessId;
    private final String businessName;
    private final String contactEmail;
    private final String contactPhone;
    private final String registrationNumber;
    private final BusinessStatus status;
    private final LocalDateTime registeredAt;
    private final LocalDateTime updatedAt;

    public CarWashBusiness(
            String businessId,
            String businessName,
            String contactEmail,
            String contactPhone,
            String registrationNumber,
            BusinessStatus status,
            LocalDateTime registeredAt,
            LocalDateTime updatedAt
    ) {
        this.businessId = businessId;
        this.businessName = businessName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.registrationNumber = registrationNumber;
        this.status = Objects.requireNonNull(status, "Business status is required");
        this.registeredAt = Objects.requireNonNull(registeredAt, "Registration time is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Update time is required");
    }

    public String getBusinessId() {
        return businessId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public BusinessStatus getStatus() {
        return status;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return status == BusinessStatus.ACTIVE;
    }

    public CarWashBusiness updateDetails(
            String businessName,
            String contactEmail,
            String contactPhone,
            String registrationNumber,
            LocalDateTime changedAt
    ) {
        return new CarWashBusiness(
                businessId,
                businessName,
                contactEmail,
                contactPhone,
                registrationNumber,
                status,
                registeredAt,
                changedAt
        );
    }

    public CarWashBusiness activate(LocalDateTime changedAt) {
        return withStatus(BusinessStatus.ACTIVE, changedAt);
    }

    public CarWashBusiness deactivate(LocalDateTime changedAt) {
        return withStatus(BusinessStatus.INACTIVE, changedAt);
    }

    private CarWashBusiness withStatus(BusinessStatus newStatus, LocalDateTime changedAt) {
        return new CarWashBusiness(
                businessId,
                businessName,
                contactEmail,
                contactPhone,
                registrationNumber,
                newStatus,
                registeredAt,
                changedAt
        );
    }
}
