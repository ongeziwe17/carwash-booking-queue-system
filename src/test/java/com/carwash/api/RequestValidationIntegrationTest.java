package com.carwash.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RequestValidationIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void configureMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/").with(jwt()
                        .jwt(jwt -> jwt.subject("request-validation-admin").claim("role", "PLATFORM_ADMIN"))
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                                new SimpleGrantedAuthority("PERM_SERVICE_READ"),
                                new SimpleGrantedAuthority("PERM_SERVICE_MANAGE"),
                                new SimpleGrantedAuthority("PERM_QUEUE_OPERATE"))))
                .apply(springSecurity())
                .build();
    }

    @Test
    void everyRequestContractRejectsInvalidInput() throws Exception {
        String prefix = "validation-" + UUID.randomUUID();
        String userId = prefix + "-user";
        String vehicleId = prefix + "-vehicle";
        String serviceId = prefix + "-service";
        String bookingId = prefix + "-booking";
        String queueId = prefix + "-queue";
        LocalDateTime scheduled = LocalDateTime.now().plusDays(30).withNano(0);

        assertValidation(post("/api/users").with(anonymous()), Map.of(
                "userId", prefix + "-invalid-user", "fullName", "User", "email", "not-email",
                "phone", "1", "password", "LocalTestPassword123!"), "email");
        assertValidation(post("/api/auth/login").with(anonymous()), Map.of(
                "email", "not-email", "password", "LocalTestPassword123!"), "email");

        createUser(userId, prefix + "@example.com");

        Map<String, Object> invalidVehicle = new HashMap<>();
        invalidVehicle.put("userId", userId);
        invalidVehicle.put("vehicleId", prefix + "-invalid-vehicle");
        invalidVehicle.put("plateNumber", " ");
        invalidVehicle.put("vehicleType", "SUV");
        invalidVehicle.put("brand", "Toyota");
        invalidVehicle.put("model", "RAV4");
        invalidVehicle.put("notes", "x".repeat(501));
        assertValidation(post("/api/vehicles"), invalidVehicle, "notes");

        createVehicle(userId, vehicleId, "VAL-" + UUID.randomUUID().toString().substring(0, 8));
        assertValidation(put("/api/vehicles/{id}", vehicleId), Map.of(
                "plateNumber", prefix + "-plate", "vehicleType", "SUV", "brand", "Toyota",
                "model", " ", "color", "Black", "notes", "ok"), "model");

        assertValidation(post("/api/services"), Map.of(
                "serviceId", prefix + "-invalid-service", "serviceName", "Wash",
                "description", "", "price", 0, "estimatedDurationMin", 0), "estimatedDurationMin");

        createService(serviceId);
        assertValidation(put("/api/services/{id}", serviceId), Map.of(
                "serviceName", "Wash", "description", "", "price", -1,
                "estimatedDurationMin", 30), "price");

        Map<String, Object> invalidBooking = new HashMap<>();
        invalidBooking.put("bookingId", prefix + "-invalid-booking");
        invalidBooking.put("userId", userId);
        invalidBooking.put("vehicleId", vehicleId);
        invalidBooking.put("serviceId", serviceId);
        invalidBooking.put("specialRequest", "x".repeat(1001));
        assertValidation(post("/api/bookings"), invalidBooking, "scheduledDateTime");

        createBooking(bookingId, userId, vehicleId, serviceId, scheduled);
        Map<String, Object> invalidBookingUpdate = new HashMap<>();
        invalidBookingUpdate.put("vehicleId", vehicleId);
        invalidBookingUpdate.put("serviceId", " ");
        invalidBookingUpdate.put("scheduledDateTime", scheduled.plusDays(1).toString());
        invalidBookingUpdate.put("specialRequest", "ok");
        assertValidation(put("/api/bookings/{id}", bookingId), invalidBookingUpdate, "serviceId");

        assertValidation(post("/api/queue-entries"), Map.of(
                "queueEntryId", prefix + "-invalid-queue", "bookingId", bookingId,
                "serviceId", serviceId, "position", 0), "position");

        createQueueEntry(queueId, bookingId, serviceId);
        assertValidation(put("/api/queue-entries/{id}/position", queueId), Map.of("position", -1), "position");

        assertValidation(put("/api/admin/users/{userId}/role", userId), Map.of(), "roleName");
    }

    private void createUser(String userId, String email) throws Exception {
        mockMvc.perform(post("/api/users").with(anonymous()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId, "fullName", "Validation User", "email", email,
                                "phone", "123", "password", "LocalTestPassword123!"))))
                .andExpect(status().isCreated());
    }

    private void createVehicle(String userId, String vehicleId, String plate) throws Exception {
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId, "vehicleId", vehicleId, "plateNumber", plate,
                                "vehicleType", "SUV", "brand", "Toyota", "model", "RAV4",
                                "color", "Black", "notes", ""))))
                .andExpect(status().isCreated());
    }

    private void createService(String serviceId) throws Exception {
        mockMvc.perform(post("/api/services").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serviceId", serviceId, "serviceName", "Validation Wash",
                                "description", "", "price", 100, "estimatedDurationMin", 30))))
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
                        .content(objectMapper.writeValueAsString(Map.of(
                                "bookingId", bookingId, "userId", userId, "vehicleId", vehicleId,
                                "serviceId", serviceId, "scheduledDateTime", scheduled.toString(),
                                "specialRequest", ""))))
                .andExpect(status().isCreated());
    }

    private void createQueueEntry(String queueId, String bookingId, String serviceId) throws Exception {
        mockMvc.perform(post("/api/queue-entries").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "queueEntryId", queueId, "bookingId", bookingId,
                                "serviceId", serviceId, "position", 1))))
                .andExpect(status().isCreated());
    }

    private void assertValidation(MockHttpServletRequestBuilder request, Map<String, Object> body, String field)
            throws Exception {
        mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == '" + field + "')]").exists());
    }
}
