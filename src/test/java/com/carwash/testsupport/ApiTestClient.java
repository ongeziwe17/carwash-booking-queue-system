package com.carwash.testsupport;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.queue.api.dto.CreateQueueEntryRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.vehicle.api.dto.CreateVehicleRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public final class ApiTestClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final AuthenticationTestClient authentication;

    public ApiTestClient(MockMvc mockMvc, ObjectMapper objectMapper, AuthenticationTestClient authentication) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.authentication = authentication;
    }

    public ResultActions createUser(CreateUserRequest request) throws Exception {
        return mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    public ResultActions createVehicle(CreateVehicleRequest request) throws Exception {
        return mockMvc.perform(post("/api/vehicles")
                .with(authentication.platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    public ResultActions createService(CreateServiceRequest request) throws Exception {
        return mockMvc.perform(post("/api/services")
                .with(authentication.platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    public ResultActions activateService(String serviceId) throws Exception {
        return mockMvc.perform(post("/api/services/{id}/activate", serviceId)
                .with(authentication.platformAdminJwt()));
    }

    public ResultActions deactivateService(String serviceId) throws Exception {
        return mockMvc.perform(post("/api/services/{id}/deactivate", serviceId)
                .with(authentication.platformAdminJwt()));
    }

    public ResultActions createBooking(CreateBookingRequest request) throws Exception {
        return mockMvc.perform(post("/api/bookings")
                .with(authentication.platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    public ResultActions createQueueEntry(CreateQueueEntryRequest request) throws Exception {
        return mockMvc.perform(post("/api/queue-entries")
                .with(authentication.platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    public ResultActions createBusiness(CreateBusinessRequest request) throws Exception {
        return mockMvc.perform(post("/api/marketplace/businesses")
                .with(authentication.platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    public ResultActions createBranch(String businessId, CreateBranchRequest request) throws Exception {
        return mockMvc.perform(post("/api/marketplace/businesses/{businessId}/branches", businessId)
                .with(authentication.platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
