package com.carwash.catalog.api;

import com.carwash.catalog.api.dto.CreateServiceOfferingRequest;
import com.carwash.catalog.api.dto.UpdateServiceOfferingRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.marketplace.api.dto.UpdateBranchRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ServiceOfferingWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void completeOfferingWorkflowUsesBranchTermsAndLifecycleAcrossAllSevenOperations() throws Exception {
        Fixture fixture = createFixture(true);
        CreateServiceOfferingRequest request = offering(
                ids.offering(), fixture.serviceId(), "125.50", 45, 3);

        createOffering(fixture.branchId(), request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.offeringId").value(request.offeringId()))
                .andExpect(jsonPath("$.branchId").value(fixture.branchId()))
                .andExpect(jsonPath("$.serviceId").value(fixture.serviceId()))
                .andExpect(jsonPath("$.serviceName").value("Exterior Wash"))
                .andExpect(jsonPath("$.price").value(125.50))
                .andExpect(jsonPath("$.estimatedDurationMin").value(45))
                .andExpect(jsonPath("$.concurrentCapacity").value(3))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.effectiveActive").value(true))
                .andExpect(jsonPath("$.discoverable").value(true));

        mockMvc.perform(get("/api/marketplace/branches/{branchId}/offerings", fixture.branchId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offeringId").value(request.offeringId()));
        mockMvc.perform(get("/api/marketplace/offerings/{offeringId}", request.offeringId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceDescription").value("Reusable global definition"));

        UpdateServiceOfferingRequest update = new UpdateServiceOfferingRequest(
                new BigDecimal("149.99"), 60, 5);
        mockMvc.perform(put("/api/marketplace/offerings/{offeringId}", request.offeringId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offeringId").value(request.offeringId()))
                .andExpect(jsonPath("$.branchId").value(fixture.branchId()))
                .andExpect(jsonPath("$.serviceId").value(fixture.serviceId()))
                .andExpect(jsonPath("$.price").value(149.99))
                .andExpect(jsonPath("$.estimatedDurationMin").value(60))
                .andExpect(jsonPath("$.concurrentCapacity").value(5));

        mockMvc.perform(get("/api/marketplace/branches/{branchId}/offerings/discoverable", fixture.branchId())
                        .with(reader("customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offeringId").value(request.offeringId()))
                .andExpect(jsonPath("$[0].price").value(149.99))
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].effectiveActive").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist());

        mockMvc.perform(post("/api/marketplace/offerings/{offeringId}/deactivate", request.offeringId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        assertDiscoverySize(fixture.branchId(), 0);
        mockMvc.perform(post("/api/marketplace/offerings/{offeringId}/activate", request.offeringId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertDiscoverySize(fixture.branchId(), 1);
    }

    @Test
    void sameGlobalServiceHasIndependentTermsAtTwoBranches() throws Exception {
        Fixture first = createFixture(true);
        String secondBusiness = ids.business();
        String secondBranch = ids.branch();
        api.createBusiness(business(secondBusiness)).andExpect(status().isCreated());
        api.createBranch(secondBusiness, branch(secondBranch, true)).andExpect(status().isCreated());

        CreateServiceOfferingRequest firstOffering = offering(
                "offering-z", first.serviceId(), "100.00", 30, 2);
        CreateServiceOfferingRequest secondOffering = offering(
                "offering-a", first.serviceId(), "175.00", 60, 6);
        createOffering(first.branchId(), firstOffering).andExpect(status().isCreated());
        createOffering(secondBranch, secondOffering).andExpect(status().isCreated());

        mockMvc.perform(get("/api/marketplace/offerings/{offeringId}", firstOffering.offeringId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(100.00))
                .andExpect(jsonPath("$.estimatedDurationMin").value(30))
                .andExpect(jsonPath("$.concurrentCapacity").value(2));
        mockMvc.perform(get("/api/marketplace/offerings/{offeringId}", secondOffering.offeringId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(175.00))
                .andExpect(jsonPath("$.estimatedDurationMin").value(60))
                .andExpect(jsonPath("$.concurrentCapacity").value(6));
    }

    @Test
    void inactiveOrPrivateParentsSuppressDiscoveryWithoutRewritingOfferingStatus() throws Exception {
        Fixture fixture = createFixture(true);
        String offeringId = ids.offering();
        createOffering(fixture.branchId(), offering(
                offeringId, fixture.serviceId(), "100.00", 30, 2)).andExpect(status().isCreated());

        api.deactivateService(fixture.serviceId()).andExpect(status().isOk());
        assertDiscoverySize(fixture.branchId(), 0);
        assertStoredOfferingStillActive(offeringId);
        api.activateService(fixture.serviceId()).andExpect(status().isOk());
        assertDiscoverySize(fixture.branchId(), 1);

        lifecycle("/api/marketplace/branches/{id}/deactivate", fixture.branchId());
        assertDiscoverySize(fixture.branchId(), 0);
        assertStoredOfferingStillActive(offeringId);
        lifecycle("/api/marketplace/branches/{id}/activate", fixture.branchId());
        assertDiscoverySize(fixture.branchId(), 1);

        lifecycle("/api/marketplace/businesses/{id}/deactivate", fixture.businessId());
        assertDiscoverySize(fixture.branchId(), 0);
        assertStoredOfferingStillActive(offeringId);
        lifecycle("/api/marketplace/businesses/{id}/activate", fixture.businessId());
        assertDiscoverySize(fixture.branchId(), 1);

        mockMvc.perform(put("/api/marketplace/branches/{branchId}", fixture.branchId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBranch(false))))
                .andExpect(status().isOk());
        assertDiscoverySize(fixture.branchId(), 0);
        assertStoredOfferingStillActive(offeringId);
        mockMvc.perform(put("/api/marketplace/branches/{branchId}", fixture.branchId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBranch(true))))
                .andExpect(status().isOk());
        assertDiscoverySize(fixture.branchId(), 1);
    }

    @Test
    void duplicateRelationshipsReferencesAndImmutableUpdateFieldsUseStandardErrors() throws Exception {
        Fixture fixture = createFixture(true);
        CreateServiceOfferingRequest offering = offering(
                ids.offering(), fixture.serviceId(), "100.00", 30, 2);
        createOffering(fixture.branchId(), offering).andExpect(status().isCreated());

        createOffering(fixture.branchId(), offering)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        createOffering(fixture.branchId(), offering(
                ids.offering(), fixture.serviceId(), "90.00", 20, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Branch already has an offering for this service; reactivate or update it instead"));
        createOffering("missing-branch", offering(
                ids.offering(), fixture.serviceId(), "90.00", 20, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        createOffering(fixture.branchId(), offering(
                ids.offering(), "missing-service", "90.00", 20, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/marketplace/offerings/{offeringId}", "missing-offering")
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        String identityInjection = """
                {"price":110.00,"estimatedDurationMin":35,"concurrentCapacity":3,
                 "branchId":"other","serviceId":"other","status":"INACTIVE"}
                """;
        mockMvc.perform(put("/api/marketplace/offerings/{offeringId}", offering.offeringId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identityInjection))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void invalidTermsAndMissingFieldsAreRejectedWithoutFallbackToGlobalDefaults() throws Exception {
        Fixture fixture = createFixture(true);

        for (CreateServiceOfferingRequest invalid : java.util.List.of(
                offering(" ", fixture.serviceId(), "100.00", 30, 2),
                offering("x".repeat(65), fixture.serviceId(), "100.00", 30, 2),
                offering(ids.offering(), "x".repeat(65), "100.00", 30, 2),
                offering(ids.offering(), fixture.serviceId(), "-0.01", 30, 2),
                offering(ids.offering(), fixture.serviceId(), "1.001", 30, 2),
                offering(ids.offering(), fixture.serviceId(), "100.00", 0, 2),
                offering(ids.offering(), fixture.serviceId(), "100.00", 30, 0),
                offering(ids.offering(), fixture.serviceId(), "100.00", 1441, 2),
                offering(ids.offering(), fixture.serviceId(), "100.00", 30, 1001)
        )) {
            createOffering(fixture.branchId(), invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        mockMvc.perform(post("/api/marketplace/branches/{branchId}/offerings", fixture.branchId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringId\":\"missing-terms\",\"serviceId\":\"" + fixture.serviceId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void referencedGlobalServiceCannotBeDeletedEvenWhenOfferingIsInactive() throws Exception {
        Fixture fixture = createFixture(true);
        String offeringId = ids.offering();
        createOffering(fixture.branchId(), offering(
                offeringId, fixture.serviceId(), "100.00", 30, 2)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/marketplace/offerings/{offeringId}/deactivate", offeringId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/services/{serviceId}", fixture.serviceId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "Service referenced by a branch offering cannot be deleted; deactivate it instead"));
    }

    private Fixture createFixture(boolean publicDiscovery) throws Exception {
        String businessId = ids.business();
        String branchId = ids.branch();
        String serviceId = ids.service();
        api.createBusiness(business(businessId)).andExpect(status().isCreated());
        api.createBranch(businessId, branch(branchId, publicDiscovery)).andExpect(status().isCreated());
        api.createService(new CreateServiceRequest(
                serviceId, "Exterior Wash", "Reusable global definition", BigDecimal.valueOf(80), 20))
                .andExpect(status().isCreated());
        return new Fixture(businessId, branchId, serviceId);
    }

    private org.springframework.test.web.servlet.ResultActions createOffering(
            String branchId,
            CreateServiceOfferingRequest request
    ) throws Exception {
        return mockMvc.perform(post("/api/marketplace/branches/{branchId}/offerings", branchId)
                .with(authentication.platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private void assertDiscoverySize(String branchId, int size) throws Exception {
        mockMvc.perform(get("/api/marketplace/branches/{branchId}/offerings/discoverable", branchId)
                        .with(reader("customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(size));
    }

    private void assertStoredOfferingStillActive(String offeringId) throws Exception {
        mockMvc.perform(get("/api/marketplace/offerings/{offeringId}", offeringId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.discoverable").value(false));
    }

    private void lifecycle(String path, String id) throws Exception {
        mockMvc.perform(post(path, id).with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor reader(String subject) {
        return authentication.roleJwt(subject, "CUSTOMER", "ROLE_CUSTOMER", "PERM_MARKETPLACE_READ");
    }

    private CreateBusinessRequest business(String id) {
        return new CreateBusinessRequest(
                id, "Wash Group", "owner@example.test", "+27 82 123 4567", "REG-" + id);
    }

    private CreateBranchRequest branch(String id, boolean publicDiscovery) {
        return new CreateBranchRequest(
                id, "City Bowl", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241), "Africa/Johannesburg", publicDiscovery);
    }

    private UpdateBranchRequest updateBranch(boolean publicDiscovery) {
        return new UpdateBranchRequest(
                "City Bowl", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241), "Africa/Johannesburg", publicDiscovery);
    }

    private CreateServiceOfferingRequest offering(
            String offeringId,
            String serviceId,
            String price,
            int duration,
            int capacity
    ) {
        return new CreateServiceOfferingRequest(
                offeringId, serviceId, new BigDecimal(price), duration, capacity);
    }

    private record Fixture(String businessId, String branchId, String serviceId) {
    }
}
