package com.carwash.catalog.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class Service {
    private String serviceId;
    private String serviceName;
    private String description;
    private BigDecimal price;
    private int estimatedDurationMin;
    private boolean isActive;
    private LocalDateTime createdAt;

    public Service() {
    }

    public Service(String serviceId, String serviceName, String description, BigDecimal price, int estimatedDurationMin) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.description = description;
        this.price = price;
        this.estimatedDurationMin = estimatedDurationMin;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
    }

    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }

    public void updateDetails(String serviceName, String description, BigDecimal price, int estimatedDurationMin) {
        this.serviceName = serviceName;
        this.description = description;
        this.price = price;
        this.estimatedDurationMin = estimatedDurationMin;
    }

    public LocalDateTime calculateEstimatedEndTime(LocalDateTime startTime) {
        return startTime.plusMinutes(estimatedDurationMin);
    }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
