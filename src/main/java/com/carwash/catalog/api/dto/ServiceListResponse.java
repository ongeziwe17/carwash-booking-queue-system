package com.carwash.catalog.api.dto;
import com.carwash.catalog.application.ServiceDefinitionSnapshot;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record ServiceListResponse(String serviceId, String serviceName, String description, BigDecimal price,
                                  int estimatedDurationMin, boolean active, LocalDateTime createdAt) {
    public static ServiceListResponse from(ServiceDefinitionSnapshot s) { return new ServiceListResponse(s.serviceId(), s.serviceName(),
            s.description(), s.legacyDefaultPrice(), s.legacyDefaultEstimatedDurationMin(), s.active(), s.createdAt()); }
}
