package com.carwash.api;

import com.carwash.domain.Booking;
import com.carwash.domain.Service;
import com.carwash.domain.Vehicle;
import com.carwash.service.BookingManagementService;
import com.carwash.service.ServiceCatalogService;
import com.carwash.service.UserManagementService;
import com.carwash.service.VehicleManagementService;
import com.carwash.service.command.CreateUserCommand;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.TestDates;
import com.carwash.testsupport.UserFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

class Sec003RemediationIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired UserManagementService users;
    @Autowired VehicleManagementService vehicles;
    @Autowired ServiceCatalogService services;
    @Autowired BookingManagementService bookings;

    @Test
    void customerCannotTransferExistingBookingToAnotherCustomer() throws Exception {
        String ownerId = register();
        String otherId = register();
        String ownerVehicleId = ids.vehicle();
        String otherVehicleId = ids.vehicle();
        String serviceId = ids.service();
        String bookingId = ids.booking();

        vehicles.createVehicle(new Vehicle(ownerVehicleId, ids.plate(), "SUV", "Toyota", "Rav4", "Black", ""), ownerId);
        vehicles.createVehicle(new Vehicle(otherVehicleId, ids.plate(), "SUV", "Honda", "CR-V", "White", ""), otherId);
        services.createService(new Service(serviceId, "Transfer Test Wash", "security regression", BigDecimal.valueOf(150), 30));
        bookings.createBooking(bookingId, ownerId, ownerVehicleId, serviceId, TestDates.futureDays(2), "original request");

        Map<String, Object> transferRequest = new LinkedHashMap<>();
        transferRequest.put("vehicleId", otherVehicleId);
        transferRequest.put("serviceId", serviceId);
        transferRequest.put("scheduledDateTime", TestDates.futureDays(3).toString());
        transferRequest.put("specialRequest", "attempted transfer");

        mockMvc.perform(put("/api/bookings/{id}", bookingId)
                        .with(authentication.customerJwt(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Vehicle does not belong to booking owner"));

        Booking unchanged = bookings.findById(bookingId);
        assertEquals(ownerId, unchanged.getUser().getUserId());
        assertEquals(ownerVehicleId, unchanged.getVehicle().getVehicleId());
    }

    @Test
    void invalidRoleNameReturnsSafeBadRequest() throws Exception {
        String targetUserId = ids.user();
        mockMvc.perform(put("/api/admin/users/{userId}/role", targetUserId)
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed or invalid request body"))
                .andExpect(jsonPath("$.path").value("/api/admin/users/" + targetUserId + "/role"))
                .andExpect(content().string(not(containsString("HttpMessageNotReadableException"))))
                .andExpect(content().string(not(containsString("RoleName"))));
    }

    private String register() {
        String userId = ids.user();
        users.createUser(new CreateUserCommand(userId, "Security Test User", ids.emailFor(userId),
                "0821234567", UserFixtureBuilder.DEFAULT_PASSWORD));
        return userId;
    }
}
