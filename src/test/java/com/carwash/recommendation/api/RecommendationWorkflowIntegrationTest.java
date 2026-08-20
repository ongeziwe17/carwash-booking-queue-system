package com.carwash.recommendation.api;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.catalog.api.dto.CreateServiceOfferingRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.marketplace.api.dto.CreateTemporaryClosureRequest;
import com.carwash.marketplace.api.dto.ReplaceOperatingHoursRequest;
import com.carwash.marketplace.api.dto.WeeklyOperatingIntervalRequest;
import com.carwash.queue.api.dto.CreateQueueEntryRequest;
import com.carwash.recommendation.application.RecommendationPreference;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(RecommendationWorkflowIntegrationTest.FixedClockConfiguration.class)
class RecommendationWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    private static final String AT = "2090-01-15T09:00:00+02:00";
    private static final String FUTURE_AT = "2090-01-22T09:00:00+02:00";

    @Test
    void preferencesRankOneEligibleSetWithIndependentBranchTermsAndSafeExplanations() throws Exception {
        Fixture fixture = fixture(true);
        Map<RecommendationPreference, String> expectedWinner = Map.of(
                RecommendationPreference.NEAREST, fixture.nearBranch(),
                RecommendationPreference.SHORTEST_QUEUE, fixture.nearBranch(),
                RecommendationPreference.FASTEST_TOTAL_TIME, fixture.farBranch(),
                RecommendationPreference.LOWEST_PRICE, fixture.farBranch(),
                RecommendationPreference.BEST_OVERALL, fixture.nearBranch());
        Set<String> expectedCandidates = Set.of(fixture.nearBranch(), fixture.farBranch());

        for (RecommendationPreference preference : RecommendationPreference.values()) {
            JsonNode body = body(recommend(fixture, preference, AT).andExpect(status().isOk()).andReturn());
            assertEquals(2, body.size());
            assertEquals(expectedWinner.get(preference), body.get(0).path("branchId").asText());
            assertEquals(expectedCandidates,
                    Set.of(body.get(0).path("branchId").asText(), body.get(1).path("branchId").asText()));
            for (int index = 0; index < body.size(); index++) {
                JsonNode item = body.get(index);
                assertEquals(index + 1, item.path("rank").asInt());
                assertFalse(item.path("businessName").asText().isBlank());
                assertEquals(preference.name(), item.path("appliedPreference").asText());
                assertFalse(item.path("explanation").asText().isBlank());
                assertEquals(true, item.path("scoreBreakdown").path("distance").has("weightedContribution"));
                assertFalse(item.has("reason"));
                assertFalse(item.has("effectiveActive"));
                assertFalse(item.has("repository"));
                assertFalse(item.has("securityContext"));
            }
        }
    }

    @Test
    void lifecycleClosureCapacityAndCancellationReuseAuthoritativeAvailability() throws Exception {
        Fixture fixture = fixture(false);

        mockMvc.perform(post("/api/marketplace/branches/{id}/deactivate", fixture.nearBranch())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        assertRecommendedBranches(fixture, fixture.farBranch());
        mockMvc.perform(post("/api/marketplace/branches/{id}/activate", fixture.nearBranch())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/marketplace/offerings/{id}/deactivate", fixture.farOffering())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        assertRecommendedBranches(fixture, fixture.nearBranch());
        mockMvc.perform(post("/api/marketplace/offerings/{id}/activate", fixture.farOffering())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/marketplace/businesses/{id}/deactivate", fixture.nearBusiness())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        assertRecommendedBranches(fixture, fixture.farBranch());
        mockMvc.perform(post("/api/marketplace/businesses/{id}/activate", fixture.nearBusiness())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/marketplace/branches/{id}/closures", fixture.nearBranch())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTemporaryClosureRequest(
                                "rec-closure", OffsetDateTime.parse("2090-01-15T09:15:00+02:00"),
                                OffsetDateTime.parse("2090-01-15T09:30:00+02:00"), "Maintenance"))))
                .andExpect(status().isCreated());
        assertRecommendedBranches(fixture, fixture.farBranch());
        mockMvc.perform(post("/api/marketplace/closures/{id}/cancel", "rec-closure")
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());

        CreateBookingRequest full = booking(
                fixture.consumerUser(), fixture.consumerVehicle(), fixture.nearBranch(), fixture.nearOffering(),
                LocalDateTime.of(2090, 1, 15, 9, 0));
        api.createBooking(full).andExpect(status().isCreated());
        assertRecommendedBranches(fixture, fixture.farBranch());
        mockMvc.perform(post("/api/bookings/{id}/cancel", full.bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        assertRecommendedBranches(fixture, fixture.nearBranch(), fixture.farBranch());
    }

    @Test
    void futureQueueAndTotalStayNullWithoutRemovingCandidates() throws Exception {
        Fixture fixture = fixture(true);

        recommend(fixture, RecommendationPreference.SHORTEST_QUEUE, FUTURE_AT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].branchId", contains(fixture.nearBranch(), fixture.farBranch())))
                .andExpect(jsonPath("$[0].queueWaitEstimateMin").doesNotExist())
                .andExpect(jsonPath("$[0].estimatedTotalTimeMin").doesNotExist())
                .andExpect(jsonPath("$[0].scoreBreakdown.queueWait.available").value(false))
                .andExpect(jsonPath("$[0].scoreBreakdown.queueWait.normalizedScore").value(0.0))
                .andExpect(jsonPath("$[0].explanation").value(
                        org.hamcrest.Matchers.containsString("unavailable")));
    }

    @Test
    void returnedRecommendationCanCreateABookingAtTheEvaluatedSnapshot() throws Exception {
        Fixture fixture = fixture(false);
        MvcResult result = recommend(fixture, RecommendationPreference.NEAREST, AT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branchId").value(fixture.nearBranch()))
                .andReturn();
        JsonNode selected = body(result).get(0);

        api.createBooking(booking(
                        fixture.consumerUser(), fixture.consumerVehicle(), selected.path("branchId").asText(),
                        selected.path("serviceOfferingId").asText(), LocalDateTime.of(2090, 1, 15, 9, 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchId").value(fixture.nearBranch()))
                .andExpect(jsonPath("$.serviceOfferingId").value(fixture.nearOffering()));
    }

    @Test
    void validationNoCandidatesAndRbacUseStandardContracts() throws Exception {
        Fixture fixture = fixture(false);
        mockMvc.perform(get("/api/recommendations/branches")
                        .param("latitude", "-33.9249").param("longitude", "18.4241")
                        .param("serviceId", fixture.serviceId()).param("at", AT)
                        .param("preference", "NEAREST"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/recommendations/branches")
                        .with(authentication.roleJwt("staff", "STAFF", "ROLE_STAFF"))
                        .param("latitude", "-33.9249").param("longitude", "18.4241")
                        .param("serviceId", fixture.serviceId()).param("at", AT)
                        .param("preference", "NEAREST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        for (Map.Entry<String, String> invalid : Map.of(
                "latitude", "91",
                "longitude", "181",
                "maxRadiusKm", "0",
                "preference", "POPULAR",
                "at", "not-a-time").entrySet()) {
            Map<String, String> parameters = validParameters(fixture);
            parameters.put(invalid.getKey(), invalid.getValue());
            var request = get("/api/recommendations/branches").with(reader());
            parameters.forEach(request::param);
            mockMvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        mockMvc.perform(get("/api/recommendations/branches")
                        .with(reader()).param("latitude", "-33.9249").param("longitude", "18.4241")
                        .param("serviceId", fixture.serviceId()).param("at", AT)
                        .param("preference", "NEAREST").param("maxRadiusKm", "50.01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        mockMvc.perform(get("/api/recommendations/branches")
                        .with(reader()).param("latitude", "-33.9249").param("longitude", "18.4241")
                        .param("serviceId", "missing-service").param("at", AT)
                        .param("preference", "NEAREST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post("/api/services/{id}/deactivate", fixture.serviceId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        recommend(fixture, RecommendationPreference.NEAREST, AT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private Fixture fixture(boolean queuedFarBranch) throws Exception {
        String nearBusiness = "rec-business-a";
        String farBusiness = "rec-business-b";
        String nearBranch = "rec-branch-a";
        String farBranch = "rec-branch-b";
        String serviceId = "rec-service";
        String nearOffering = "rec-offering-a";
        String farOffering = "rec-offering-b";
        api.createBusiness(new CreateBusinessRequest(
                nearBusiness, "Recommendation Group A", "rec-a@example.test", "+27821234001", null))
                .andExpect(status().isCreated());
        api.createBusiness(new CreateBusinessRequest(
                farBusiness, "Recommendation Group B", "rec-b@example.test", "+27821234002", null))
                .andExpect(status().isCreated());
        api.createBranch(nearBusiness, branch(nearBranch, "Cape Town", "-33.9249", "18.4241"))
                .andExpect(status().isCreated());
        api.createBranch(farBusiness, branch(farBranch, "Sea Point", "-33.9173", "18.3884"))
                .andExpect(status().isCreated());
        ReplaceOperatingHoursRequest hours = new ReplaceOperatingHoursRequest(List.of(
                new WeeklyOperatingIntervalRequest(
                        DayOfWeek.SUNDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        api.replaceOperatingHours(nearBranch, hours).andExpect(status().isOk());
        api.replaceOperatingHours(farBranch, hours).andExpect(status().isOk());
        api.createService(new CreateServiceRequest(
                serviceId, "Recommendation Wash", "Exact service match", new BigDecimal("80.00"), 30))
                .andExpect(status().isCreated());
        api.createServiceOffering(nearBranch, new CreateServiceOfferingRequest(
                nearOffering, serviceId, new BigDecimal("200.00"), 60, 1))
                .andExpect(status().isCreated());
        api.createServiceOffering(farBranch, new CreateServiceOfferingRequest(
                farOffering, serviceId, new BigDecimal("100.00"), 20, 2))
                .andExpect(status().isCreated());

        Identity queueIdentity = identity();
        Identity consumerIdentity = identity();
        if (queuedFarBranch) {
            CreateBookingRequest queued = booking(
                    queueIdentity.userId(), queueIdentity.vehicleId(), farBranch, farOffering,
                    LocalDateTime.of(2090, 1, 15, 9, 0));
            api.createBooking(queued).andExpect(status().isCreated());
            mockMvc.perform(post("/api/bookings/{id}/confirm", queued.bookingId())
                            .with(authentication.platformAdminJwt()))
                    .andExpect(status().isOk());
            api.createQueueEntry(new CreateQueueEntryRequest(
                            ids.queueEntry(), queued.bookingId(), serviceId))
                    .andExpect(status().isCreated());
        }
        return new Fixture(
                nearBusiness, nearBranch, farBranch, serviceId, nearOffering, farOffering,
                consumerIdentity.userId(), consumerIdentity.vehicleId());
    }

    private Identity identity() throws Exception {
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());
        CreateVehicleRequest vehicle = VehicleFixtureBuilder.valid(ids, user.userId()).build();
        api.createVehicle(vehicle).andExpect(status().isCreated());
        return new Identity(user.userId(), vehicle.vehicleId());
    }

    private CreateBookingRequest booking(
            String userId,
            String vehicleId,
            String branchId,
            String offeringId,
            LocalDateTime startsAt
    ) {
        return BookingFixtureBuilder.valid(ids, userId, vehicleId, branchId, offeringId)
                .scheduledDateTime(startsAt)
                .build();
    }

    private CreateBranchRequest branch(String id, String name, String latitude, String longitude) {
        return new CreateBranchRequest(
                id, name, "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                new BigDecimal(latitude), new BigDecimal(longitude), "Africa/Johannesburg", true);
    }

    private org.springframework.test.web.servlet.ResultActions recommend(
            Fixture fixture,
            RecommendationPreference preference,
            String at
    ) throws Exception {
        return mockMvc.perform(get("/api/recommendations/branches")
                .with(reader())
                .param("latitude", "-33.9249")
                .param("longitude", "18.4241")
                .param("serviceId", fixture.serviceId())
                .param("at", at)
                .param("preference", preference.name()));
    }

    private void assertRecommendedBranches(Fixture fixture, String... expected) throws Exception {
        recommend(fixture, RecommendationPreference.NEAREST, AT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].branchId", contains(expected)));
    }

    private RequestPostProcessor reader() {
        return authentication.roleJwt(
                "recommendation-customer", "CUSTOMER", "ROLE_CUSTOMER", "PERM_MARKETPLACE_READ");
    }

    private Map<String, String> validParameters(Fixture fixture) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("latitude", "-33.9249");
        parameters.put("longitude", "18.4241");
        parameters.put("serviceId", fixture.serviceId());
        parameters.put("at", AT);
        parameters.put("preference", "NEAREST");
        return parameters;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record Identity(String userId, String vehicleId) {
    }

    private record Fixture(
            String nearBusiness,
            String nearBranch,
            String farBranch,
            String serviceId,
            String nearOffering,
            String farOffering,
            String consumerUser,
            String consumerVehicle
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock recommendationClock() {
            return Clock.fixed(Instant.parse("2090-01-15T06:00:00Z"), ZoneOffset.UTC);
        }
    }
}
