package com.carwash.access.api;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.catalog.api.dto.CreateServiceOfferingRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.marketplace.api.dto.ReplaceOperatingHoursRequest;
import com.carwash.marketplace.api.dto.UpdateBranchRequest;
import com.carwash.marketplace.api.dto.WeeklyOperatingIntervalRequest;
import com.carwash.queue.api.dto.CreateQueueEntryRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.TestDates;
import com.carwash.testsupport.UserFixtureBuilder;
import com.carwash.testsupport.VehicleFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantIsolationIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void ownersStaffCustomersAndAdministratorsRemainInsideTheirCanonicalScopes() throws Exception {
        Fixture fixture = fixture();
        RequestPostProcessor ownerA = owner("owner-a", fixture.tenantA().businessId());
        RequestPostProcessor ownerB = owner("owner-b", fixture.tenantB().businessId());
        RequestPostProcessor staffA = staff("staff-a", fixture.tenantA().businessId());
        RequestPostProcessor staffB = staff("staff-b", fixture.tenantB().businessId());

        mockMvc.perform(get("/api/marketplace/businesses/{id}", fixture.tenantA().businessId()).with(ownerA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/marketplace/branches/{id}", fixture.tenantA().branchId()).with(ownerA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/marketplace/offerings/{id}", fixture.tenantA().offeringId()).with(ownerA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/{id}", fixture.tenantA().bookingId()).with(ownerA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/queue-entries/{id}", fixture.tenantA().queueId()).with(ownerA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/daily-summary").with(ownerA)
                        .param("date", fixture.tenantA().scheduledAt().toLocalDate().toString())
                        .param("businessId", fixture.tenantA().businessId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeId").value(fixture.tenantA().businessId()));

        assertTenantNotFound(get("/api/marketplace/businesses/{id}", fixture.tenantB().businessId()).with(ownerA));
        assertTenantNotFound(put("/api/marketplace/businesses/{id}", fixture.tenantB().businessId())
                .with(ownerA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessName\":\"Foreign\",\"contactEmail\":\"foreign@example.test\","
                        + "\"contactPhone\":\"+27821234567\",\"registrationNumber\":null}"));
        assertTenantNotFound(post("/api/marketplace/businesses/{id}/activate",
                fixture.tenantB().businessId()).with(ownerA));
        assertTenantNotFound(post("/api/marketplace/businesses/{id}/deactivate",
                fixture.tenantB().businessId()).with(ownerA));
        assertTenantNotFound(get("/api/marketplace/businesses/{id}/branches",
                fixture.tenantB().businessId()).with(ownerA));
        assertTenantNotFound(post("/api/marketplace/businesses/{id}/branches",
                fixture.tenantB().businessId()).with(ownerA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateBranchRequest(
                        ids.branch(), "Foreign Branch", "1 Foreign Road", null, "Cape Town", "Western Cape",
                        "8001", "ZA", BigDecimal.valueOf(-33.92), BigDecimal.valueOf(18.42),
                        "Africa/Johannesburg", true))));
        assertTenantNotFound(get("/api/marketplace/branches/{id}", fixture.tenantB().branchId()).with(ownerA));
        assertTenantNotFound(get("/api/marketplace/offerings/{id}", fixture.tenantB().offeringId()).with(ownerA));
        assertTenantNotFound(get("/api/bookings/{id}", fixture.tenantB().bookingId()).with(ownerA));
        assertTenantNotFound(get("/api/queue-entries/{id}", fixture.tenantB().queueId()).with(ownerA));
        assertTenantNotFound(post("/api/bookings/{id}/confirm", fixture.tenantB().bookingId()).with(ownerA));
        assertTenantNotFound(post("/api/queue-entries/{id}/call", fixture.tenantB().queueId()).with(ownerA));
        assertTenantNotFound(post("/api/queue-entries/call-next").with(ownerA)
                .param("branchId", fixture.tenantB().branchId()));
        assertTenantNotFound(get("/api/marketplace/branches/{branchId}/operating-hours",
                fixture.tenantB().branchId()).with(ownerA));
        assertTenantNotFound(put("/api/marketplace/branches/{branchId}/operating-hours",
                fixture.tenantB().branchId()).with(ownerA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReplaceOperatingHoursRequest(List.of(
                        new WeeklyOperatingIntervalRequest(
                                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))))));
        assertTenantNotFound(get("/api/marketplace/branches/{branchId}/closures",
                fixture.tenantB().branchId()).with(ownerA));
        assertTenantNotFound(post("/api/marketplace/closures/{closureId}/cancel",
                fixture.tenantB().closureId()).with(ownerA));
        assertTenantNotFound(post("/api/marketplace/branches/{id}/deactivate",
                fixture.tenantB().branchId()).with(ownerA));
        assertTenantNotFound(put("/api/marketplace/offerings/{id}", fixture.tenantB().offeringId())
                .with(ownerA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":125,\"estimatedDurationMin\":30,\"concurrentCapacity\":2}"));
        assertTenantNotFound(post("/api/marketplace/branches/{branchId}/offerings",
                fixture.tenantB().branchId()).with(ownerA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceOfferingRequest(
                        ids.offering(), fixture.serviceId(), BigDecimal.valueOf(125), 30, 2))));
        assertTenantNotFound(delete("/api/bookings/{id}", fixture.tenantB().bookingId()).with(ownerA));
        assertTenantNotFound(delete("/api/queue-entries/{id}", fixture.tenantB().queueId()).with(ownerA));
        assertTenantNotFound(get("/api/reports/daily-summary").with(ownerA)
                .param("date", fixture.tenantB().scheduledAt().toLocalDate().toString())
                .param("businessId", fixture.tenantB().businessId()));

        mockMvc.perform(get("/api/bookings").with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].bookingId", hasItem(fixture.tenantA().bookingId())))
                .andExpect(jsonPath("$[*].bookingId", not(hasItem(fixture.tenantB().bookingId()))));
        mockMvc.perform(get("/api/marketplace/businesses").with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].businessId", hasItem(fixture.tenantA().businessId())))
                .andExpect(jsonPath("$[*].businessId", not(hasItem(fixture.tenantB().businessId()))));
        mockMvc.perform(get("/api/queue-entries").with(staffA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].queueEntryId", hasItem(fixture.tenantA().queueId())))
                .andExpect(jsonPath("$[*].queueEntryId", not(hasItem(fixture.tenantB().queueId()))));
        assertTenantNotFound(get("/api/bookings/{id}", fixture.tenantB().bookingId()).with(staffA));
        mockMvc.perform(get("/api/reports/daily-summary").with(staffA)
                        .param("date", fixture.tenantA().scheduledAt().toLocalDate().toString())
                        .param("businessId", fixture.tenantA().businessId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/marketplace/branches/{id}", fixture.tenantB().branchId()).with(staffA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateBranchRequest(
                                "Denied Branch", "1 Denied Street", null, "Cape Town", "Western Cape",
                                "8001", "ZA", new BigDecimal("-33.9249"), new BigDecimal("18.4241"),
                                "Africa/Johannesburg", true))))
                .andExpect(status().isForbidden());

        assertTenantNotFound(get("/api/bookings/{id}", fixture.tenantA().bookingId()).with(ownerB));
        mockMvc.perform(get("/api/bookings/{id}", fixture.tenantB().bookingId()).with(ownerB))
                .andExpect(status().isOk());
        assertTenantNotFound(get("/api/queue-entries/{id}", fixture.tenantA().queueId()).with(staffB));
        mockMvc.perform(get("/api/queue-entries").with(staffB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].queueEntryId", hasItem(fixture.tenantB().queueId())))
                .andExpect(jsonPath("$[*].queueEntryId", not(hasItem(fixture.tenantA().queueId()))));

        mockMvc.perform(get("/api/bookings/{id}", fixture.tenantA().bookingId())
                        .with(authentication.customerJwt(fixture.customerId())))
                .andExpect(status().isOk());
        assertTenantNotFound(get("/api/bookings/{id}", fixture.tenantA().bookingId())
                .with(authentication.customerJwt("other-customer")));

        mockMvc.perform(get("/api/notifications/user/{id}", fixture.customerId()).with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].branchId", everyItem(is(fixture.tenantA().branchId()))));
        mockMvc.perform(get("/api/notifications/user/{id}/inbox", fixture.customerId()).with(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[*].branchId",
                        everyItem(is(fixture.tenantA().branchId()))))
                .andExpect(jsonPath("$.unreadCount").value(1));
        mockMvc.perform(get("/api/notifications/user/{id}/inbox", fixture.customerId()).with(ownerB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[*].branchId",
                        everyItem(is(fixture.tenantB().branchId()))))
                .andExpect(jsonPath("$.unreadCount").value(1));

        assertIndistinguishableNotFound(
                get("/api/bookings/{id}", fixture.tenantB().bookingId()).with(ownerA),
                get("/api/bookings/{id}", "missing-booking").with(ownerA));

        mockMvc.perform(get("/api/bookings").with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/bookings").with(authentication.platformAdminJwt())
                        .param("businessId", fixture.tenantB().businessId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].bookingId", everyItem(is(fixture.tenantB().bookingId()))));
        mockMvc.perform(get("/api/bookings/{id}", fixture.tenantA().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/marketplace/businesses").with(ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBusinessRequest(
                                ids.business(), "Unrelated", "unrelated@example.test", "+27821234569", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void discoveryResponsesUseOnlyTheDocumentedCustomerAllowlist() throws Exception {
        Fixture fixture = fixture();
        String body = mockMvc.perform(get("/api/marketplace/branches/discoverable")
                        .with(authentication.customerJwt(fixture.customerId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Set<String> expected = Set.of(
                "branchId", "businessId", "branchName", "addressLine1", "addressLine2", "city",
                "province", "postalCode", "countryCode", "latitude", "longitude", "timezone");
        objectMapper.readTree(body).forEach(node -> {
            Set<String> actual = new HashSet<>();
            node.properties().forEach(property -> actual.add(property.getKey()));
            assertEquals(expected, actual);
        });
        for (String privateField : Set.of(
                "contactEmail", "contactPhone", "registrationNumber", "status", "effectiveActive",
                "discoverable", "publicDiscoveryEnabled", "createdAt", "updatedAt", "version",
                "memberships", "staff", "notifications", "audit")) {
            org.junit.jupiter.api.Assertions.assertFalse(body.contains("\"" + privateField + "\""));
        }
    }

    private Fixture fixture() throws Exception {
        CreateUserRequest customer = UserFixtureBuilder.valid(ids).build();
        api.createUser(customer).andExpect(status().isCreated());
        var vehicle = VehicleFixtureBuilder.valid(ids, customer.userId()).build();
        api.createVehicle(vehicle).andExpect(status().isCreated());
        CreateServiceRequest service = new CreateServiceRequest(
                ids.service(), "Tenant Wash", "Reusable platform definition", BigDecimal.valueOf(100), 30);
        api.createService(service).andExpect(status().isCreated());
        api.activateService(service.serviceId()).andExpect(status().isOk());

        TenantFixture tenantA = tenant("A", customer.userId(), vehicle.vehicleId(), service, TestDates.futureDays(150));
        TenantFixture tenantB = tenant("B", customer.userId(), vehicle.vehicleId(), service, TestDates.futureDays(151));
        return new Fixture(customer.userId(), service.serviceId(), tenantA, tenantB);
    }

    private TenantFixture tenant(
            String suffix,
            String customerId,
            String vehicleId,
            CreateServiceRequest service,
            LocalDateTime scheduledAt
    ) throws Exception {
        String businessId = ids.business();
        String branchId = ids.branch();
        String offeringId = ids.offering();
        String bookingId = ids.booking();
        String queueId = ids.queueEntry();
        String closureId = ids.closure();
        api.createBusiness(new CreateBusinessRequest(
                businessId, "Tenant " + suffix, suffix.toLowerCase() + "@tenant.test", "+27821234567", null))
                .andExpect(status().isCreated());
        api.createBranch(businessId, new CreateBranchRequest(
                branchId, "Branch " + suffix, suffix + " Main Road", null, "Cape Town", "Western Cape",
                "8001", "ZA", BigDecimal.valueOf(-33.92), BigDecimal.valueOf(18.42),
                "Africa/Johannesburg", true)).andExpect(status().isCreated());
        api.replaceOperatingHours(branchId, new ReplaceOperatingHoursRequest(
                Arrays.stream(DayOfWeek.values()).map(day -> new WeeklyOperatingIntervalRequest(
                        day, LocalTime.of(8, 0), LocalTime.of(17, 0))).toList()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/marketplace/branches/{branchId}/closures", branchId)
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.carwash.marketplace.api.dto.CreateTemporaryClosureRequest(
                                closureId,
                                scheduledAt.plusHours(3).atOffset(ZoneOffset.ofHours(2)),
                                scheduledAt.plusHours(4).atOffset(ZoneOffset.ofHours(2)),
                                "Tenant isolation fixture"))))
                .andExpect(status().isCreated());
        api.createServiceOffering(branchId, new CreateServiceOfferingRequest(
                offeringId, service.serviceId(), BigDecimal.valueOf(100), 30, 2))
                .andExpect(status().isCreated());
        api.createBooking(new CreateBookingRequest(
                bookingId, customerId, vehicleId, branchId, offeringId, scheduledAt, suffix))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/bookings/{id}/confirm", bookingId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        api.createQueueEntry(new CreateQueueEntryRequest(queueId, bookingId, service.serviceId()))
                .andExpect(status().isCreated());
        return new TenantFixture(businessId, branchId, offeringId, bookingId, queueId, closureId, scheduledAt);
    }

    private RequestPostProcessor owner(String subject, String businessId) {
        return authentication.tenantRoleJwt(subject, "BUSINESS_OWNER", businessId,
                "ROLE_BUSINESS_OWNER", "PERM_MARKETPLACE_READ", "PERM_MARKETPLACE_MANAGE",
                "PERM_VEHICLE_OPERATE", "PERM_BOOKING_OPERATE", "PERM_QUEUE_OPERATE",
                "PERM_REPORT_READ", "PERM_NOTIFICATION_SELF_READ");
    }

    private RequestPostProcessor staff(String subject, String businessId) {
        return authentication.tenantRoleJwt(subject, "STAFF", businessId,
                "ROLE_STAFF", "PERM_MARKETPLACE_READ", "PERM_VEHICLE_OPERATE",
                "PERM_BOOKING_OPERATE", "PERM_QUEUE_OPERATE", "PERM_NOTIFICATION_SELF_READ");
    }

    private void assertTenantNotFound(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        assertTenantNotFound(mockMvc.perform(request));
    }

    private void assertTenantNotFound(ResultActions action) throws Exception {
        action.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message", not(org.hamcrest.Matchers.containsString("Tenant "))));
    }

    private void assertIndistinguishableNotFound(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder foreign,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder missing
    ) throws Exception {
        var foreignBody = objectMapper.readTree(mockMvc.perform(foreign)
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString());
        var missingBody = objectMapper.readTree(mockMvc.perform(missing)
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString());
        assertEquals(foreignBody.path("status"), missingBody.path("status"));
        assertEquals(foreignBody.path("code"), missingBody.path("code"));
        assertEquals(foreignBody.path("message"), missingBody.path("message"));
    }

    private record Fixture(String customerId, String serviceId, TenantFixture tenantA, TenantFixture tenantB) {
    }

    private record TenantFixture(
            String businessId,
            String branchId,
            String offeringId,
            String bookingId,
            String queueId,
            String closureId,
            LocalDateTime scheduledAt
    ) {
    }
}
