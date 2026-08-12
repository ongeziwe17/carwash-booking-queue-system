package com.carwash.catalog.api;

import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.ServiceFixtureBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ServiceWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void serviceApiCreateAndDeactivate() throws Exception {
        CreateServiceRequest service = ServiceFixtureBuilder.valid(ids).build();
        api.createService(service).andExpect(status().isCreated());
        api.deactivateService(service.serviceId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void serviceApiGetAllReturnsUnfilteredCatalog() throws Exception {
        CreateServiceRequest active = createService("Exterior Wash", true);
        CreateServiceRequest inactive = createService("Interior Wash", false);
        String response = getServices(null);
        assertTrue(response.contains(active.serviceId()));
        assertTrue(response.contains(inactive.serviceId()));
    }

    @Test
    void serviceApiGetAllFiltersActiveServices() throws Exception {
        CreateServiceRequest active = createService("Deluxe Wash", true);
        CreateServiceRequest inactive = createService("Archived Wash", false);
        String response = getServices(true);
        assertTrue(response.contains(active.serviceId()));
        assertFalse(response.contains(inactive.serviceId()));
    }

    @Test
    void serviceApiGetAllFiltersInactiveServices() throws Exception {
        CreateServiceRequest active = createService("Express Wash", true);
        CreateServiceRequest inactive = createService("Seasonal Wash", false);
        String response = getServices(false);
        assertTrue(response.contains(inactive.serviceId()));
        assertFalse(response.contains(active.serviceId()));
    }

    private CreateServiceRequest createService(String name, boolean active) throws Exception {
        CreateServiceRequest request = ServiceFixtureBuilder.valid(ids)
                .serviceName(name)
                .price(BigDecimal.valueOf(100))
                .build();
        api.createService(request).andExpect(status().isCreated());
        if (!active) {
            api.deactivateService(request.serviceId()).andExpect(status().isOk());
        }
        return request;
    }

    private String getServices(Boolean active) throws Exception {
        var request = get("/api/services").with(authentication.platformAdminJwt());
        if (active != null) {
            request.param("active", active.toString());
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
