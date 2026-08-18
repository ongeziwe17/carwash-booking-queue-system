package com.carwash.booking.api;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.catalog.api.dto.CreateServiceOfferingRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.marketplace.api.dto.CreateTemporaryClosureRequest;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(BranchAvailabilityWorkflowIntegrationTest.FixedClockConfiguration.class)
class BranchAvailabilityWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    private static final String AT = "2090-01-15T09:00:00+02:00";

    @Test
    void searchReturnsIndependentBranchTermsAndBookingCapacityUsesTheSameDecision() throws Exception {
        Fixture fixture = fixture();

        search(fixture.serviceId(), AT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].branchId", contains(fixture.nearBranch(), fixture.farBranch())))
                .andExpect(jsonPath("$[0].serviceOfferingId").value(fixture.nearOffering()))
                .andExpect(jsonPath("$[0].price").value(100.00))
                .andExpect(jsonPath("$[0].estimatedDurationMin").value(30))
                .andExpect(jsonPath("$[0].concurrentCapacity").value(1))
                .andExpect(jsonPath("$[0].capacityRemaining").value(1))
                .andExpect(jsonPath("$[0].availableStartAt").value(AT))
                .andExpect(jsonPath("$[0].estimatedEndAt").value("2090-01-15T09:30:00+02:00"))
                .andExpect(jsonPath("$[0].queueWaitEstimateMin").value(0))
                .andExpect(jsonPath("$[0].distanceKm").isNumber())
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].reason").doesNotExist())
                .andExpect(jsonPath("$[1].price").value(175.50))
                .andExpect(jsonPath("$[1].estimatedDurationMin").value(60))
                .andExpect(jsonPath("$[1].concurrentCapacity").value(2));

        CreateBookingRequest booking = BookingFixtureBuilder.valid(
                        ids, fixture.userId(), fixture.vehicleId(), fixture.nearBranch(), fixture.nearOffering())
                .scheduledDateTime(LocalDateTime.of(2090, 1, 15, 9, 0))
                .build();
        api.createBooking(booking).andExpect(status().isCreated());
        search(fixture.serviceId(), AT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].branchId", contains(fixture.farBranch())));

        mockMvc.perform(post("/api/bookings/{id}/cancel", booking.bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        search(fixture.serviceId(), AT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].branchId", contains(fixture.nearBranch(), fixture.farBranch())));
    }

    @Test
    void completeWindowClosureAndRadiusFiltersExcludeOtherwiseEligibleBranches() throws Exception {
        Fixture fixture = fixture();
        mockMvc.perform(post("/api/marketplace/branches/{branchId}/closures", fixture.nearBranch())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTemporaryClosureRequest(
                                "availability-closure",
                                OffsetDateTime.parse("2090-01-15T09:15:00+02:00"),
                                OffsetDateTime.parse("2090-01-15T09:20:00+02:00"),
                                "Short maintenance"))))
                .andExpect(status().isCreated());

        search(fixture.serviceId(), AT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].branchId", contains(fixture.farBranch())));

        mockMvc.perform(post("/api/marketplace/closures/{closureId}/cancel", "availability-closure")
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/availability/branches")
                        .with(reader())
                        .param("serviceId", fixture.serviceId())
                        .param("at", AT)
                        .param("latitude", "-33.9249")
                        .param("longitude", "18.4241")
                        .param("radiusKm", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].branchId", contains(fixture.nearBranch())));
    }

    @Test
    void timezoneLessBookingInputIsValidatedAgainstTheSelectedBranchTimezone() throws Exception {
        String businessId = ids.business();
        String branchId = ids.branch();
        String serviceId = ids.service();
        String offeringId = ids.offering();
        api.createBusiness(new CreateBusinessRequest(
                businessId, "Pacific Availability", ids.emailFor(businessId), "+27821234567", null))
                .andExpect(status().isCreated());
        api.createBranch(businessId, new CreateBranchRequest(
                branchId, "Pacific Branch", "1 Main Road", null, "Los Angeles", "California", "90001", "US",
                new BigDecimal("34.0522"), new BigDecimal("-118.2437"), "America/Los_Angeles", true))
                .andExpect(status().isCreated());
        api.replaceOperatingHours(branchId, new ReplaceOperatingHoursRequest(List.of(
                new WeeklyOperatingIntervalRequest(
                        DayOfWeek.SUNDAY, LocalTime.of(0, 0), LocalTime.of(5, 0)))))
                .andExpect(status().isOk());
        api.createService(new CreateServiceRequest(
                serviceId, "Pacific Wash", "Timezone validation", new BigDecimal("90.00"), 30))
                .andExpect(status().isCreated());
        api.createServiceOffering(branchId, new CreateServiceOfferingRequest(
                offeringId, serviceId, new BigDecimal("110.00"), 30, 1))
                .andExpect(status().isCreated());
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());
        CreateVehicleRequest vehicle = VehicleFixtureBuilder.valid(ids, user.userId()).build();
        api.createVehicle(vehicle).andExpect(status().isCreated());

        String requestedAt = "2090-01-15T01:00:00-08:00";
        mockMvc.perform(get("/api/availability/branches")
                        .with(reader()).param("serviceId", serviceId).param("at", requestedAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].branchId", contains(branchId)))
                .andExpect(jsonPath("$[0].availableStartAt").value(requestedAt));

        CreateBookingRequest booking = BookingFixtureBuilder.valid(
                        ids, user.userId(), vehicle.vehicleId(), branchId, offeringId)
                .scheduledDateTime(LocalDateTime.of(2090, 1, 15, 1, 0))
                .build();
        api.createBooking(booking).andExpect(status().isCreated());
        mockMvc.perform(get("/api/availability/branches")
                        .with(reader()).param("serviceId", serviceId).param("at", requestedAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validationAuthenticationAndAuthorizationUseStandardErrors() throws Exception {
        Fixture fixture = fixture();
        mockMvc.perform(get("/api/availability/branches")
                        .param("serviceId", fixture.serviceId()).param("at", AT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/availability/branches")
                        .with(authentication.roleJwt("operator", "STAFF", "ROLE_STAFF"))
                        .param("serviceId", fixture.serviceId()).param("at", AT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/availability/branches")
                        .with(reader()).param("serviceId", fixture.serviceId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/availability/branches")
                        .with(reader()).param("at", AT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/availability/branches")
                        .with(reader()).param("serviceId", fixture.serviceId()).param("at", AT)
                        .param("latitude", "-33"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        mockMvc.perform(get("/api/availability/branches")
                        .with(reader()).param("serviceId", fixture.serviceId()).param("at", AT)
                        .param("radiusKm", "5"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/availability/branches")
                        .with(reader()).param("serviceId", fixture.serviceId()).param("at", AT)
                        .param("latitude", "-33").param("longitude", "18").param("radiusKm", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/availability/branches")
                        .with(reader()).param("serviceId", "missing-service").param("at", AT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/availability/branches")
                        .with(reader()).param("serviceId", fixture.serviceId()).param("at", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    private org.springframework.test.web.servlet.ResultActions search(String serviceId, String at) throws Exception {
        return mockMvc.perform(get("/api/availability/branches")
                .with(reader())
                .param("serviceId", serviceId)
                .param("at", at)
                .param("latitude", "-33.9249")
                .param("longitude", "18.4241"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor reader() {
        return authentication.roleJwt("customer", "CUSTOMER", "ROLE_CUSTOMER", "PERM_SERVICE_READ");
    }

    private Fixture fixture() throws Exception {
        String businessId = ids.business();
        String nearBranch = ids.branch();
        String farBranch = ids.branch();
        String serviceId = ids.service();
        String nearOffering = ids.offering();
        String farOffering = ids.offering();
        api.createBusiness(new CreateBusinessRequest(
                businessId, "Availability Group", ids.emailFor(businessId), "+27821234567", null))
                .andExpect(status().isCreated());
        api.createBranch(businessId, branch(nearBranch, "Cape Town", "-33.9249", "18.4241"))
                .andExpect(status().isCreated());
        api.createBranch(businessId, branch(farBranch, "Johannesburg", "-26.2041", "28.0473"))
                .andExpect(status().isCreated());
        ReplaceOperatingHoursRequest hours = new ReplaceOperatingHoursRequest(List.of(
                new WeeklyOperatingIntervalRequest(
                        DayOfWeek.SUNDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        api.replaceOperatingHours(nearBranch, hours).andExpect(status().isOk());
        api.replaceOperatingHours(farBranch, hours).andExpect(status().isOk());

        api.createService(new CreateServiceRequest(
                serviceId, "Premium Wash", "Reusable wash", new BigDecimal("80.00"), 20))
                .andExpect(status().isCreated());
        api.createServiceOffering(nearBranch, new CreateServiceOfferingRequest(
                nearOffering, serviceId, new BigDecimal("100.00"), 30, 1))
                .andExpect(status().isCreated());
        api.createServiceOffering(farBranch, new CreateServiceOfferingRequest(
                farOffering, serviceId, new BigDecimal("175.50"), 60, 2))
                .andExpect(status().isCreated());

        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());
        CreateVehicleRequest vehicle = VehicleFixtureBuilder.valid(ids, user.userId()).build();
        api.createVehicle(vehicle).andExpect(status().isCreated());
        return new Fixture(nearBranch, farBranch, serviceId, nearOffering, farOffering,
                user.userId(), vehicle.vehicleId());
    }

    private CreateBranchRequest branch(
            String branchId,
            String name,
            String latitude,
            String longitude
    ) {
        return new CreateBranchRequest(
                branchId, name, "1 Main Road", null, name, "Province", "8001", "ZA",
                new BigDecimal(latitude), new BigDecimal(longitude), "Africa/Johannesburg", true);
    }

    private record Fixture(
            String nearBranch,
            String farBranch,
            String serviceId,
            String nearOffering,
            String farOffering,
            String userId,
            String vehicleId
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock availabilityClock() {
            return Clock.fixed(Instant.parse("2090-01-15T06:00:00Z"), ZoneOffset.UTC);
        }
    }
}
