package com.carwash.testsupport;

import com.carwash.api.dto.CreateServiceRequest;

import java.math.BigDecimal;

public final class ServiceFixtureBuilder {

    private String serviceId;
    private String serviceName = "Integration Wash";
    private String description = "Integration test service";
    private BigDecimal price = BigDecimal.valueOf(150);
    private Integer estimatedDurationMin = 30;

    private ServiceFixtureBuilder() {
    }

    public static ServiceFixtureBuilder valid(TestIdFactory ids) {
        ServiceFixtureBuilder builder = new ServiceFixtureBuilder();
        builder.serviceId = ids.service();
        return builder;
    }

    public ServiceFixtureBuilder serviceId(String value) { this.serviceId = value; return this; }
    public ServiceFixtureBuilder serviceName(String value) { this.serviceName = value; return this; }
    public ServiceFixtureBuilder description(String value) { this.description = value; return this; }
    public ServiceFixtureBuilder price(BigDecimal value) { this.price = value; return this; }
    public ServiceFixtureBuilder estimatedDurationMin(Integer value) { this.estimatedDurationMin = value; return this; }

    public CreateServiceRequest build() {
        return new CreateServiceRequest(serviceId, serviceName, description, price, estimatedDurationMin);
    }
}
