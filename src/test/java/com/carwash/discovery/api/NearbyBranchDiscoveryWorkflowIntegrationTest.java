package com.carwash.discovery.api;

import com.carwash.catalog.api.dto.CreateServiceOfferingRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.marketplace.api.dto.CreateTemporaryClosureRequest;
import com.carwash.marketplace.api.dto.ReplaceOperatingHoursRequest;
import com.carwash.marketplace.api.dto.WeeklyOperatingIntervalRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NearbyBranchDiscoveryWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    private static final String OPEN_AT = "2030-01-07T10:00:00+02:00";

    @Test
    void customerDiscoversNearbyBranchesWithCombinedDeterministicFilters() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                        .with(reader("customer", "CUSTOMER"))
                        .param("latitude", "-33.9249")
                        .param("longitude", "18.4241"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].branchId", contains(fixture.capeTownBranch(), fixture.johannesburgBranch())))
                .andExpect(jsonPath("$[0].businessId").value(fixture.activeBusiness()))
                .andExpect(jsonPath("$[0].branchName").value("Cape Town Branch"))
                .andExpect(jsonPath("$[0].latitude").value(-33.9249))
                .andExpect(jsonPath("$[0].longitude").value(18.4241))
                .andExpect(jsonPath("$[0].timezone").value("Africa/Johannesburg"))
                .andExpect(jsonPath("$[0].distanceKm").isNumber())
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].publicDiscoveryEnabled").doesNotExist())
                .andExpect(jsonPath("$[0].offerings").doesNotExist());

        mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                        .with(reader("staff", "STAFF"))
                        .param("latitude", "-33.9249")
                        .param("longitude", "18.4241")
                        .param("radiusKm", "100")
                        .param("serviceId", fixture.serviceId())
                        .param("openAt", OPEN_AT)
                        .param("sort", "distance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].branchId").value(fixture.capeTownBranch()))
                .andExpect(jsonPath("$[0].distanceKm").value(0.00));
    }

    @Test
    void offeringAndScheduleLifecycleControlOptionalFiltersWithoutChangingVisibilityRules() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(post("/api/marketplace/offerings/{offeringId}/deactivate", fixture.capeTownOffering())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        assertFilteredSize(fixture, "serviceId", fixture.serviceId(), 1);
        mockMvc.perform(post("/api/marketplace/offerings/{offeringId}/activate", fixture.capeTownOffering())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        assertFilteredSize(fixture, "serviceId", fixture.serviceId(), 2);

        mockMvc.perform(post("/api/services/{serviceId}/deactivate", fixture.serviceId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        assertFilteredSize(fixture, "serviceId", fixture.serviceId(), 0);
        mockMvc.perform(post("/api/services/{serviceId}/activate", fixture.serviceId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());

        assertFilteredSize(fixture, "openAt", OPEN_AT, 1);
        String closureId = "closure-cape";
        mockMvc.perform(post("/api/marketplace/branches/{branchId}/closures", fixture.capeTownBranch())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTemporaryClosureRequest(
                                closureId,
                                OffsetDateTime.parse("2030-01-07T09:00:00+02:00"),
                                OffsetDateTime.parse("2030-01-07T11:00:00+02:00"),
                                "Maintenance"))))
                .andExpect(status().isCreated());
        assertFilteredSize(fixture, "openAt", OPEN_AT, 0);
        mockMvc.perform(post("/api/marketplace/closures/{closureId}/cancel", closureId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        assertFilteredSize(fixture, "openAt", OPEN_AT, 1);
    }

    @Test
    void invalidDiscoveryParametersAndUnknownServiceUseStandardErrors() throws Exception {
        createFixture();
        RequestPostProcessor reader = reader("customer", "CUSTOMER");

        mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                        .with(reader).param("longitude", "18"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
        mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                        .with(reader).param("latitude", "-33").param("longitude", "NaN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                        .with(reader).param("latitude", "91").param("longitude", "18"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                        .with(reader).param("latitude", "-33").param("longitude", "-181"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        for (QueryParameter invalid : List.of(
                new QueryParameter("radiusKm", "0"),
                new QueryParameter("radiusKm", "20000.01"),
                new QueryParameter("serviceId", "x".repeat(65)),
                new QueryParameter("sort", "name")
        )) {
            mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                            .with(reader)
                            .param("latitude", "-33")
                            .param("longitude", "18")
                            .param(invalid.name(), invalid.value()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                        .with(reader)
                        .param("latitude", "-33")
                        .param("longitude", "18")
                        .param("serviceId", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                        .with(reader)
                        .param("latitude", "-33")
                        .param("longitude", "18")
                        .param("serviceId", "missing-service"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                        .with(reader)
                        .param("latitude", "-33")
                        .param("longitude", "18")
                        .param("openAt", "not-an-instant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void endpointRequiresMarketplaceReadPermissionAndAuthentication() throws Exception {
        mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                        .param("latitude", "-33").param("longitude", "18"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                        .with(authentication.roleJwt("operator", "STAFF", "ROLE_STAFF"))
                        .param("latitude", "-33").param("longitude", "18"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private Fixture createFixture() throws Exception {
        String activeBusiness = "business-active";
        String inactiveBusiness = "business-inactive";
        String capeTown = "branch-cape-town";
        String johannesburg = "branch-johannesburg";
        String privateBranch = "branch-private";
        String inactiveBranch = "branch-inactive-parent";
        String serviceId = "service-exterior";
        String capeOffering = "offering-cape";

        api.createBusiness(business(activeBusiness, "active@example.test")).andExpect(status().isCreated());
        api.createBusiness(business(inactiveBusiness, "inactive@example.test")).andExpect(status().isCreated());
        api.createBranch(activeBusiness, branch(capeTown, "Cape Town Branch", "-33.9249", "18.4241", true))
                .andExpect(status().isCreated());
        api.createBranch(activeBusiness, branch(johannesburg, "Johannesburg Branch", "-26.2041", "28.0473", true))
                .andExpect(status().isCreated());
        api.createBranch(activeBusiness, branch(privateBranch, "Private Branch", "-33.93", "18.43", false))
                .andExpect(status().isCreated());
        api.createBranch(inactiveBusiness, branch(inactiveBranch, "Inactive Parent", "-33.92", "18.42", true))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/marketplace/businesses/{businessId}/deactivate", inactiveBusiness)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());

        api.createService(new CreateServiceRequest(
                serviceId, "Exterior", "Reusable definition", BigDecimal.valueOf(80), 20))
                .andExpect(status().isCreated());
        api.createServiceOffering(capeTown, offering(capeOffering, serviceId)).andExpect(status().isCreated());
        api.createServiceOffering(johannesburg, offering("offering-joburg", serviceId)).andExpect(status().isCreated());

        replaceHours(capeTown, List.of(new WeeklyOperatingIntervalRequest(
                DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        replaceHours(johannesburg, List.of());
        return new Fixture(activeBusiness, capeTown, johannesburg, serviceId, capeOffering);
    }

    private void replaceHours(String branchId, List<WeeklyOperatingIntervalRequest> intervals) throws Exception {
        mockMvc.perform(put("/api/marketplace/branches/{branchId}/operating-hours", branchId)
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReplaceOperatingHoursRequest(intervals))))
                .andExpect(status().isOk());
    }

    private void assertFilteredSize(Fixture fixture, String parameter, String value, int size) throws Exception {
        mockMvc.perform(get("/api/marketplace/branches/discoverable/nearby")
                        .with(reader("customer", "CUSTOMER"))
                        .param("latitude", "-30")
                        .param("longitude", "23")
                        .param(parameter, value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(size));
    }

    private CreateBusinessRequest business(String id, String email) {
        return new CreateBusinessRequest(id, "Wash Group " + id, email, "+27 82 123 4567", "REG-" + id);
    }

    private CreateBranchRequest branch(
            String id,
            String name,
            String latitude,
            String longitude,
            boolean publicDiscovery
    ) {
        return new CreateBranchRequest(
                id, name, "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                new BigDecimal(latitude), new BigDecimal(longitude), "Africa/Johannesburg", publicDiscovery);
    }

    private CreateServiceOfferingRequest offering(String id, String serviceId) {
        return new CreateServiceOfferingRequest(id, serviceId, BigDecimal.valueOf(100), 30, 2);
    }

    private RequestPostProcessor reader(String subject, String role) {
        return authentication.roleJwt(subject, role,
                "ROLE_" + role, "PERM_MARKETPLACE_READ");
    }

    private record QueryParameter(String name, String value) {
    }

    private record Fixture(
            String activeBusiness,
            String capeTownBranch,
            String johannesburgBranch,
            String serviceId,
            String capeTownOffering
    ) {
    }
}
