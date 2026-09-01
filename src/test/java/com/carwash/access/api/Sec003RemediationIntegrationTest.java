package com.carwash.access.api;

import com.carwash.identity.domain.RoleName;
import com.carwash.identity.domain.User;

import com.carwash.booking.domain.Booking;
import com.carwash.catalog.domain.Service;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.booking.application.BookingManagementService;
import com.carwash.catalog.application.ServiceCatalogService;
import com.carwash.catalog.application.CreateServiceOfferingCommand;
import com.carwash.catalog.application.ServiceOfferingService;
import com.carwash.marketplace.application.CreateBranchCommand;
import com.carwash.marketplace.application.MarketplaceManagementService;
import com.carwash.marketplace.application.BranchSchedulingService;
import com.carwash.marketplace.application.ReplaceOperatingScheduleCommand;
import com.carwash.marketplace.application.WeeklyOperatingIntervalCommand;
import com.carwash.marketplace.application.RegisterBusinessCommand;
import com.carwash.identity.application.UserManagementService;
import com.carwash.vehicle.application.VehicleManagementService;
import com.carwash.identity.application.CreateUserCommand;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.TestDates;
import com.carwash.testsupport.TestAccess;
import com.carwash.testsupport.UserFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
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
    @Autowired ServiceOfferingService offerings;
    @Autowired MarketplaceManagementService marketplace;
    @Autowired BranchSchedulingService branchScheduling;

    @Test
    void customerCannotTransferExistingBookingToAnotherCustomer() throws Exception {
        String ownerId = register();
        String otherId = register();
        String ownerVehicleId = ids.vehicle();
        String otherVehicleId = ids.vehicle();
        String serviceId = ids.service();
        String bookingId = ids.booking();

        vehicles.createVehicle(TestAccess.platformAdministrator(), ownerId, ownerVehicleId,
                ids.plate(), "SUV", "Toyota", "Rav4", "Black", "");
        vehicles.createVehicle(TestAccess.platformAdministrator(), otherId, otherVehicleId,
                ids.plate(), "SUV", "Honda", "CR-V", "White", "");
        services.createService(new Service(serviceId, "Transfer Test Wash", "security regression", BigDecimal.valueOf(150), 30));
        String businessId = ids.business();
        marketplace.registerBusiness(TestAccess.platformAdministrator(), new RegisterBusinessCommand(
                businessId, "Security Wash", ids.emailFor(businessId), "+27821234567", null));
        String branchId = ids.branch();
        marketplace.createBranch(TestAccess.platformAdministrator(), businessId, new CreateBranchCommand(
                branchId, "Security Branch", "1 Test Street", null, "Cape Town", "Western Cape", "8001", "ZA",
                new BigDecimal("-33.9249"), new BigDecimal("18.4241"), "Africa/Johannesburg", true));
        branchScheduling.replaceOperatingSchedule(TestAccess.platformAdministrator(), branchId, new ReplaceOperatingScheduleCommand(
                Arrays.stream(DayOfWeek.values())
                        .map(day -> new WeeklyOperatingIntervalCommand(
                                day, LocalTime.of(8, 0), LocalTime.of(17, 0)))
                        .toList()));
        String offeringId = ids.offering();
        offerings.createOffering(TestAccess.platformAdministrator(), branchId, new CreateServiceOfferingCommand(
                offeringId, serviceId, BigDecimal.valueOf(150), 30, 2));
        bookings.createBooking(TestAccess.platformAdministrator(),
                bookingId, ownerId, ownerVehicleId, branchId, offeringId,
                TestDates.futureDays(2), "original request");

        Map<String, Object> transferRequest = new LinkedHashMap<>();
        transferRequest.put("vehicleId", otherVehicleId);
        transferRequest.put("serviceOfferingId", offeringId);
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
