package com.carwash.booking.api;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.booking.api.dto.RescheduleBookingRequest;
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
import org.springframework.http.MediaType;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(BranchAvailabilityDstOverlapIntegrationTest.FixedClockConfiguration.class)
class BranchAvailabilityDstOverlapIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void availabilityExcludesBothFallBackOccurrencesAndAdvertisesOnlyBookableControls() throws Exception {
        String businessId = ids.business();
        String branchId = ids.branch();
        String serviceId = ids.service();
        String offeringId = ids.offering();
        api.createBusiness(new CreateBusinessRequest(
                businessId, "New York Availability", ids.emailFor(businessId), "+12125550123", null))
                .andExpect(status().isCreated());
        api.createBranch(businessId, new CreateBranchRequest(
                branchId, "New York Branch", "1 Main Street", null, "New York", "New York", "10001", "US",
                new BigDecimal("40.7128"), new BigDecimal("-74.0060"), "America/New_York", true))
                .andExpect(status().isCreated());
        api.replaceOperatingHours(branchId, new ReplaceOperatingHoursRequest(List.of(
                new WeeklyOperatingIntervalRequest(
                        DayOfWeek.SUNDAY, LocalTime.of(0, 0), LocalTime.of(4, 0)))))
                .andExpect(status().isOk());
        api.createService(new CreateServiceRequest(
                serviceId, "DST Wash", "DST booking consistency", new BigDecimal("80.00"), 30))
                .andExpect(status().isCreated());
        api.createServiceOffering(branchId, new CreateServiceOfferingRequest(
                offeringId, serviceId, new BigDecimal("100.00"), 30, 4))
                .andExpect(status().isCreated());
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());
        CreateVehicleRequest vehicle = VehicleFixtureBuilder.valid(ids, user.userId()).build();
        api.createVehicle(vehicle).andExpect(status().isCreated());

        search(serviceId, "2090-11-05T01:30:00-04:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        search(serviceId, "2090-11-05T01:30:00-05:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        CreateBookingRequest ambiguous = BookingFixtureBuilder.valid(
                        ids, user.userId(), vehicle.vehicleId(), branchId, offeringId)
                .scheduledDateTime(LocalDateTime.of(2090, 11, 5, 1, 30))
                .build();
        api.createBooking(ambiguous)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "Scheduled date/time is ambiguous in the branch timezone"));

        String beforeOverlapBookingId = assertAdvertisedAndBookable(
                serviceId, branchId, offeringId, user.userId(), vehicle.vehicleId(),
                "2090-11-05T00:30:00-04:00", LocalDateTime.of(2090, 11, 5, 0, 30));
        assertAdvertisedAndBookable(
                serviceId, branchId, offeringId, user.userId(), vehicle.vehicleId(),
                "2090-11-05T02:30:00-05:00", LocalDateTime.of(2090, 11, 5, 2, 30));

        mockMvc.perform(post("/api/bookings/{id}/reschedule", beforeOverlapBookingId)
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RescheduleBookingRequest(
                                LocalDateTime.of(2090, 11, 5, 1, 30)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "Scheduled date/time is ambiguous in the branch timezone"));
    }

    private String assertAdvertisedAndBookable(
            String serviceId,
            String branchId,
            String offeringId,
            String userId,
            String vehicleId,
            String requestedAt,
            LocalDateTime branchLocalStart
    ) throws Exception {
        search(serviceId, requestedAt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].branchId", contains(branchId)))
                .andExpect(jsonPath("$[0].availableStartAt").value(requestedAt))
                .andExpect(jsonPath("$[0].reason").doesNotExist());

        CreateBookingRequest booking = BookingFixtureBuilder.valid(
                        ids, userId, vehicleId, branchId, offeringId)
                .scheduledDateTime(branchLocalStart)
                .build();
        api.createBooking(booking)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchId").value(branchId))
                .andExpect(jsonPath("$.serviceOfferingId").value(offeringId));
        return booking.bookingId();
    }

    private org.springframework.test.web.servlet.ResultActions search(String serviceId, String requestedAt)
            throws Exception {
        return mockMvc.perform(get("/api/availability/branches")
                .with(authentication.roleJwt(
                        "dst-customer", "CUSTOMER", "ROLE_CUSTOMER", "PERM_SERVICE_READ"))
                .param("serviceId", serviceId)
                .param("at", requestedAt));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock dstAvailabilityClock() {
            return Clock.fixed(Instant.parse("2090-11-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
