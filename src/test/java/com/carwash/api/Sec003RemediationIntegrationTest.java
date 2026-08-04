package com.carwash.api;

import com.carwash.api.dto.CreateBookingRequest;
import com.carwash.domain.Booking;
import com.carwash.domain.Service;
import com.carwash.domain.Vehicle;
import com.carwash.service.BookingManagementService;
import com.carwash.service.ServiceCatalogService;
import com.carwash.service.UserManagementService;
import com.carwash.service.VehicleManagementService;
import com.carwash.service.command.CreateUserCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Sec003RemediationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserManagementService users;

    @Autowired
    VehicleManagementService vehicles;

    @Autowired
    ServiceCatalogService services;

    @Autowired
    BookingManagementService bookings;

    @Test
    void customerCannotTransferExistingBookingToAnotherCustomer() throws Exception {
        String prefix = "transfer-" + UUID.randomUUID();
        String ownerId = prefix + "-owner";
        String otherId = prefix + "-other";
        String ownerVehicleId = prefix + "-owner-vehicle";
        String otherVehicleId = prefix + "-other-vehicle";
        String serviceId = prefix + "-service";
        String bookingId = prefix + "-booking";

        register(ownerId, prefix + "-owner@example.com");
        register(otherId, prefix + "-other@example.com");
        vehicles.createVehicle(new Vehicle(ownerVehicleId, prefix + "-owner-plate", "SUV",
                "Toyota", "Rav4", "Black", ""), ownerId);
        vehicles.createVehicle(new Vehicle(otherVehicleId, prefix + "-other-plate", "SUV",
                "Honda", "CR-V", "White", ""), otherId);
        services.createService(new Service(serviceId, "Transfer Test Wash", "security regression",
                BigDecimal.valueOf(150), 30));

        Booking booking = new CreateBookingRequest(
                bookingId,
                ownerId,
                ownerVehicleId,
                serviceId,
                LocalDateTime.now().plusDays(2),
                "original request"
        ).toBooking();
        bookings.createBooking(booking);

        Map<String, Object> transferRequest = new LinkedHashMap<>();
        transferRequest.put("bookingId", "replacement-booking-id");
        transferRequest.put("userId", otherId);
        transferRequest.put("vehicleId", otherVehicleId);
        transferRequest.put("serviceId", serviceId);
        transferRequest.put("scheduledDateTime", LocalDateTime.now().plusDays(3).toString());
        transferRequest.put("specialRequest", "attempted transfer");
        transferRequest.put("status", "COMPLETED");
        transferRequest.put("createdAt", LocalDateTime.now().minusDays(2).toString());
        transferRequest.put("queueEntry", Map.of("queueEntryId", "injected"));

        mockMvc.perform(put("/api/bookings/{id}", bookingId)
                        .with(customerJwt(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Vehicle does not belong to booking owner"));

        Booking unchanged = bookings.findById(bookingId);
        assertEquals(ownerId, unchanged.getUser().getUserId());
        assertEquals(ownerVehicleId, unchanged.getVehicle().getVehicleId());
    }

    @Test
    void invalidRoleNameReturnsSafeBadRequest() throws Exception {
        String targetUserId = "invalid-role-" + UUID.randomUUID();

        mockMvc.perform(put("/api/admin/users/{userId}/role", targetUserId)
                        .with(platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.path").value("/api/admin/users/" + targetUserId + "/role"))
                .andExpect(content().string(not(containsString("HttpMessageNotReadableException"))))
                .andExpect(content().string(not(containsString("RoleName"))));
    }

    private void register(String userId, String email) {
        users.createUser(new CreateUserCommand(
                userId,
                "Security Test User",
                email,
                "0821234567",
                "LocalTestPassword123!"
        ));
    }

    private RequestPostProcessor customerJwt(String userId) {
        return jwt()
                .jwt(token -> token.subject(userId).claim("role", "CUSTOMER"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private RequestPostProcessor platformAdminJwt() {
        return jwt()
                .jwt(token -> token.subject("security-remediation-admin").claim("role", "PLATFORM_ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }
}
