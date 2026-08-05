package com.carwash.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class DataIntegrityIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void configureMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/").with(jwt()
                        .jwt(token -> token.subject("data-integrity-admin").claim("role", "PLATFORM_ADMIN"))
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                                new SimpleGrantedAuthority("PERM_SERVICE_READ"),
                                new SimpleGrantedAuthority("PERM_SERVICE_MANAGE"),
                                new SimpleGrantedAuthority("PERM_QUEUE_OPERATE"))))
                .apply(springSecurity())
                .build();
    }

    @Test
    void duplicateIdsReturnBusinessRuleViolationWithoutReplacingData() throws Exception {
        String prefix = "dup-" + UUID.randomUUID().toString().substring(0, 8);
        String userId = prefix + "-user";
        String vehicleId = prefix + "-vehicle";
        String serviceId = prefix + "-service";
        String bookingId = prefix + "-booking";
        String queueId = prefix + "-queue";

        createUser(userId, prefix + "@example.com");
        assertBusinessRule(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content(userJson(userId, prefix + "-replacement@example.com")),
                "/api/users", "User ID already exists");

        createVehicle(userId, vehicleId, "PLATE-" + prefix);
        assertBusinessRule(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                .content(vehicleJson(userId, vehicleId, "OTHER-" + prefix)),
                "/api/vehicles", "Vehicle ID already exists");
        mockMvc.perform(get("/api/vehicles/" + vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plateNumber").value("PLATE-" + prefix));

        createService(serviceId);
        assertBusinessRule(post("/api/services").contentType(MediaType.APPLICATION_JSON)
                .content(serviceJson(serviceId, "Replacement")),
                "/api/services", "Service ID already exists");

        LocalDateTime scheduled = LocalDateTime.now().plusDays(40).withNano(0);
        createBooking(bookingId, userId, vehicleId, serviceId, scheduled);
        assertBusinessRule(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson(bookingId, userId, vehicleId, serviceId, scheduled, "Replacement")),
                "/api/bookings", "Booking ID already exists");

        createQueue(queueId, bookingId, serviceId);
        assertBusinessRule(post("/api/queue-entries").contentType(MediaType.APPLICATION_JSON)
                .content(queueJson(queueId, bookingId, serviceId, 2)),
                "/api/queue-entries", "Queue entry ID already exists");
    }

    @Test
    void referencedResourcesReturnStandardDependencyViolationContract() throws Exception {
        String prefix = "dep-" + UUID.randomUUID().toString().substring(0, 8);
        String userId = prefix + "-user";
        String vehicleId = prefix + "-vehicle";
        String serviceId = prefix + "-service";
        String bookingId = prefix + "-booking";

        createUser(userId, prefix + "@example.com");
        createVehicle(userId, vehicleId, "PLATE-" + prefix);
        createService(serviceId);
        createBooking(bookingId, userId, vehicleId, serviceId,
                LocalDateTime.now().plusDays(41).withNano(0));

        assertBusinessRule(delete("/api/users/" + userId), "/api/users/" + userId,
                "User cannot be deleted while vehicles or bookings still reference it");
        assertBusinessRule(delete("/api/vehicles/" + vehicleId), "/api/vehicles/" + vehicleId,
                "Vehicle cannot be deleted while bookings reference it");
        assertBusinessRule(delete("/api/services/" + serviceId), "/api/services/" + serviceId,
                "Referenced service cannot be deleted; deactivate it instead");
    }

    @Test
    void terminalBookingAndQueueMutationsReturnBusinessRuleViolation() throws Exception {
        String prefix = "life-" + UUID.randomUUID().toString().substring(0, 8);
        String userId = prefix + "-user";
        String vehicleId = prefix + "-vehicle";
        String serviceId = prefix + "-service";
        String bookingId = prefix + "-booking";
        String queueId = prefix + "-queue";
        LocalDateTime scheduled = LocalDateTime.now().plusDays(42).withNano(0);

        createUser(userId, prefix + "@example.com");
        createVehicle(userId, vehicleId, "PLATE-" + prefix);
        createService(serviceId);
        createBooking(bookingId, userId, vehicleId, serviceId, scheduled);

        createQueue(queueId, bookingId, serviceId);
        mockMvc.perform(post("/api/queue-entries/" + queueId + "/call-next"))
                .andExpect(status().isOk());
        assertBusinessRule(put("/api/queue-entries/" + queueId + "/position")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"position\":2}"),
                "/api/queue-entries/" + queueId + "/position",
                "Only waiting queue entries can be repositioned");
        assertBusinessRule(delete("/api/queue-entries/" + queueId),
                "/api/queue-entries/" + queueId,
                "Only waiting queue entries can be deleted");

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isOk());
        assertBusinessRule(put("/api/bookings/" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBookingJson(vehicleId, serviceId, scheduled.plusDays(1))),
                "/api/bookings/" + bookingId,
                "Booking cannot be updated in its current state");
    }

    private void assertBusinessRule(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String path,
            String message
    ) throws Exception {
        ResultActions result = mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.fieldErrors").isArray());
        String timestamp = objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .get("timestamp").asString();
        assertDoesNotThrow(() -> Instant.parse(timestamp));
    }

    private void createUser(String userId, String email) throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(userJson(userId, email)))
                .andExpect(status().isCreated());
    }

    private void createVehicle(String userId, String vehicleId, String plate) throws Exception {
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(userId, vehicleId, plate)))
                .andExpect(status().isCreated());
    }

    private void createService(String serviceId) throws Exception {
        mockMvc.perform(post("/api/services").contentType(MediaType.APPLICATION_JSON)
                        .content(serviceJson(serviceId, "Wash")))
                .andExpect(status().isCreated());
    }

    private void createBooking(
            String bookingId,
            String userId,
            String vehicleId,
            String serviceId,
            LocalDateTime scheduled
    ) throws Exception {
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(bookingId, userId, vehicleId, serviceId, scheduled, "Request")))
                .andExpect(status().isCreated());
    }

    private void createQueue(String queueId, String bookingId, String serviceId) throws Exception {
        mockMvc.perform(post("/api/queue-entries").contentType(MediaType.APPLICATION_JSON)
                        .content(queueJson(queueId, bookingId, serviceId, 1)))
                .andExpect(status().isCreated());
    }

    private String userJson(String userId, String email) {
        return "{\"userId\":\"" + userId + "\",\"fullName\":\"Data User\",\"email\":\""
                + email + "\",\"phone\":\"0821234567\",\"password\":\"LocalTestPassword123!\"}";
    }

    private String vehicleJson(String userId, String vehicleId, String plate) {
        return "{\"userId\":\"" + userId + "\",\"vehicleId\":\"" + vehicleId
                + "\",\"plateNumber\":\"" + plate
                + "\",\"vehicleType\":\"SEDAN\",\"brand\":\"Toyota\",\"model\":\"Corolla\"}";
    }

    private String serviceJson(String serviceId, String name) {
        return "{\"serviceId\":\"" + serviceId + "\",\"serviceName\":\"" + name
                + "\",\"description\":\"Description\",\"price\":100.00,\"estimatedDurationMin\":30}";
    }

    private String bookingJson(
            String bookingId,
            String userId,
            String vehicleId,
            String serviceId,
            LocalDateTime scheduled,
            String request
    ) {
        return "{\"bookingId\":\"" + bookingId + "\",\"userId\":\"" + userId
                + "\",\"vehicleId\":\"" + vehicleId + "\",\"serviceId\":\"" + serviceId
                + "\",\"scheduledDateTime\":\"" + scheduled + "\",\"specialRequest\":\"" + request + "\"}";
    }

    private String updateBookingJson(String vehicleId, String serviceId, LocalDateTime scheduled) {
        return "{\"vehicleId\":\"" + vehicleId + "\",\"serviceId\":\"" + serviceId
                + "\",\"scheduledDateTime\":\"" + scheduled + "\",\"specialRequest\":\"Updated\"}";
    }

    private String queueJson(String queueId, String bookingId, String serviceId, int position) {
        return "{\"queueEntryId\":\"" + queueId + "\",\"bookingId\":\"" + bookingId
                + "\",\"serviceId\":\"" + serviceId + "\",\"position\":" + position + "}";
    }
}
