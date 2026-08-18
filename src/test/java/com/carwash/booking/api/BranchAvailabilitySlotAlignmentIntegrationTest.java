package com.carwash.booking.api;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.catalog.api.dto.CreateServiceOfferingRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.marketplace.api.dto.ReplaceOperatingHoursRequest;
import com.carwash.marketplace.api.dto.WeeklyOperatingIntervalRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingFixtureBuilder;
import com.carwash.testsupport.UserFixtureBuilder;
import com.carwash.testsupport.VehicleFixtureBuilder;
import com.carwash.vehicle.api.dto.CreateVehicleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(BranchAvailabilitySlotAlignmentIntegrationTest.FixedClockConfiguration.class)
@TestPropertySource(properties = "carwash.policy.booking.slot-interval=PT45M")
class BranchAvailabilitySlotAlignmentIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void searchAndBookingUseContinuousSplitAndOvernightOperatingWindowAnchors() throws Exception {
        String businessId = ids.business();
        String branchId = ids.branch();
        String serviceId = ids.service();
        String offeringId = ids.offering();
        api.createBusiness(new CreateBusinessRequest(
                businessId, "Alignment Group", ids.emailFor(businessId), "+27821234567", null))
                .andExpect(status().isCreated());
        api.createBranch(businessId, new CreateBranchRequest(
                branchId, "Alignment Branch", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                new BigDecimal("-33.9249"), new BigDecimal("18.4241"), "Africa/Johannesburg", true))
                .andExpect(status().isCreated());
        api.replaceOperatingHours(branchId, new ReplaceOperatingHoursRequest(List.of(
                new WeeklyOperatingIntervalRequest(
                        DayOfWeek.SUNDAY, LocalTime.of(8, 0), LocalTime.of(10, 0)),
                new WeeklyOperatingIntervalRequest(
                        DayOfWeek.SUNDAY, LocalTime.of(10, 0), LocalTime.of(12, 0)),
                new WeeklyOperatingIntervalRequest(
                        DayOfWeek.SUNDAY, LocalTime.of(13, 0), LocalTime.of(17, 0)),
                new WeeklyOperatingIntervalRequest(
                        DayOfWeek.SUNDAY, LocalTime.of(20, 0), LocalTime.of(2, 0))
        ))).andExpect(status().isOk());
        api.createService(new CreateServiceRequest(
                serviceId, "Alignment Wash", "Window-relative slots", new BigDecimal("80.00"), 30))
                .andExpect(status().isCreated());
        api.createServiceOffering(branchId, new CreateServiceOfferingRequest(
                offeringId, serviceId, new BigDecimal("100.00"), 30, 10))
                .andExpect(status().isCreated());
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());
        CreateVehicleRequest vehicle = VehicleFixtureBuilder.valid(ids, user.userId()).build();
        api.createVehicle(vehicle).andExpect(status().isCreated());

        assertSearchAndBookingAgree(
                serviceId, branchId, offeringId, user.userId(), vehicle.vehicleId(),
                "2090-01-15T08:00:00+02:00", LocalDateTime.of(2090, 1, 15, 8, 0), true);
        assertSearchAndBookingAgree(
                serviceId, branchId, offeringId, user.userId(), vehicle.vehicleId(),
                "2090-01-15T08:45:00+02:00", LocalDateTime.of(2090, 1, 15, 8, 45), true);
        assertSearchAndBookingAgree(
                serviceId, branchId, offeringId, user.userId(), vehicle.vehicleId(),
                "2090-01-15T08:15:00+02:00", LocalDateTime.of(2090, 1, 15, 8, 15), false);

        assertSearchAndBookingAgree(
                serviceId, branchId, offeringId, user.userId(), vehicle.vehicleId(),
                "2090-01-15T10:15:00+02:00", LocalDateTime.of(2090, 1, 15, 10, 15), true);
        assertSearchAndBookingAgree(
                serviceId, branchId, offeringId, user.userId(), vehicle.vehicleId(),
                "2090-01-15T13:00:00+02:00", LocalDateTime.of(2090, 1, 15, 13, 0), true);
        assertSearchAndBookingAgree(
                serviceId, branchId, offeringId, user.userId(), vehicle.vehicleId(),
                "2090-01-15T13:15:00+02:00", LocalDateTime.of(2090, 1, 15, 13, 15), false);
        assertSearchAndBookingAgree(
                serviceId, branchId, offeringId, user.userId(), vehicle.vehicleId(),
                "2090-01-15T13:45:00+02:00", LocalDateTime.of(2090, 1, 15, 13, 45), true);

        assertSearchAndBookingAgree(
                serviceId, branchId, offeringId, user.userId(), vehicle.vehicleId(),
                "2090-01-15T20:00:00+02:00", LocalDateTime.of(2090, 1, 15, 20, 0), true);
        assertSearchAndBookingAgree(
                serviceId, branchId, offeringId, user.userId(), vehicle.vehicleId(),
                "2090-01-16T00:30:00+02:00", LocalDateTime.of(2090, 1, 16, 0, 30), true);
    }

    private void assertSearchAndBookingAgree(
            String serviceId,
            String branchId,
            String offeringId,
            String userId,
            String vehicleId,
            String requestedAt,
            LocalDateTime branchLocalStartsAt,
            boolean expectedAvailable
    ) throws Exception {
        var search = mockMvc.perform(get("/api/availability/branches")
                .with(authentication.roleJwt("customer", "CUSTOMER", "ROLE_CUSTOMER", "PERM_SERVICE_READ"))
                .param("serviceId", serviceId)
                .param("at", requestedAt));
        if (expectedAvailable) {
            search.andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].branchId", contains(branchId)));
        } else {
            search.andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        CreateBookingRequest booking = BookingFixtureBuilder.valid(
                        ids, userId, vehicleId, branchId, offeringId)
                .scheduledDateTime(branchLocalStartsAt)
                .build();
        if (expectedAvailable) {
            api.createBooking(booking).andExpect(status().isCreated());
        } else {
            api.createBooking(booking)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(
                            "Scheduled date/time must align with the configured slot interval"));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock alignmentClock() {
            return Clock.fixed(Instant.parse("2090-01-15T05:00:00Z"), ZoneOffset.UTC);
        }
    }
}
