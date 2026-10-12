package com.carwash.catalog.api.dto;
import com.carwash.catalog.domain.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record ServiceListResponse(String serviceId, String serviceName, String description, BigDecimal price,
                                  int estimatedDurationMin, boolean active, LocalDateTime createdAt) {
    public static ServiceListResponse from(Service s) { return new ServiceListResponse(s.getServiceId(), s.getServiceName(),
            s.getDescription(), s.getPrice(), s.getEstimatedDurationMin(), s.isActive(), s.getCreatedAt()); }
}
