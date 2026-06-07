package com.carwash.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void userApiCreateAndGetAll() throws Exception {
        Map<String, Object> user = Map.of("userId", "u1", "fullName", "Test User", "email", "u1@test.com", "phone", "123", "passwordHash", "x");
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(user))).andExpect(status().isCreated());
        mockMvc.perform(get("/api/users")).andExpect(status().isOk());
    }

    @Test
    void serviceApiCreateAndDeactivate() throws Exception {
        Map<String, Object> service = new HashMap<>();
        service.put("serviceId", "s1"); service.put("serviceName", "Basic"); service.put("description", "basic wash");
        service.put("price", BigDecimal.valueOf(100)); service.put("estimatedDurationMin", 30);
        mockMvc.perform(post("/api/services").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(service))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/services/s1/deactivate")).andExpect(status().isOk());
    }

    @Test
    void bookingApiCreateConfirmAndInvalid() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"u2\",\"fullName\":\"U 2\",\"email\":\"u2@test.com\",\"phone\":\"123\",\"passwordHash\":\"x\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"u2\",\"vehicleId\":\"v2\",\"plateNumber\":\"ABC123\",\"vehicleType\":\"SUV\",\"brand\":\"Toyota\",\"model\":\"Rav4\",\"color\":\"Black\",\"notes\":\"\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/services").contentType(MediaType.APPLICATION_JSON).content("{\"serviceId\":\"s2\",\"serviceName\":\"Premium\",\"description\":\"premium\",\"price\":300,\"estimatedDurationMin\":45}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/services/s2/activate")).andExpect(status().isOk());

        String validBooking = "{\"bookingId\":\"b1\",\"userId\":\"u2\",\"vehicleId\":\"v2\",\"serviceId\":\"s2\",\"scheduledDateTime\":\"" + LocalDateTime.now().plusDays(1) + "\",\"specialRequest\":\"none\"}";
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(validBooking)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/bookings/b1/confirm")).andExpect(status().isOk());

        String invalidBooking = "{\"bookingId\":\"b2\",\"userId\":\"u2\",\"vehicleId\":\"v2\",\"serviceId\":\"s2\",\"scheduledDateTime\":\"" + LocalDateTime.now().minusDays(1) + "\",\"specialRequest\":\"none\"}";
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(invalidBooking)).andExpect(status().isBadRequest());
    }

    @Test
    void openApiDocsEndpointAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
void bookingValidationErrorIncludesFieldLevelMessage() throws Exception {
    String invalidBooking = """
            {
              "bookingId": "",
              "userId": "",
              "vehicleId": "v-test",
              "serviceId": "s-test",
              "scheduledDateTime": null,
              "specialRequest": "validation test"
            }
            """;

    mockMvc.perform(post("/api/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidBooking))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("bookingId")))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("userId")))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("scheduledDateTime")))
            .andExpect(jsonPath("$.path").value("/api/bookings"));
}
}
