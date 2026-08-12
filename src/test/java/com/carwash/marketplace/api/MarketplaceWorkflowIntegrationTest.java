package com.carwash.marketplace.api;

import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.marketplace.api.dto.UpdateBranchRequest;
import com.carwash.marketplace.api.dto.UpdateBusinessRequest;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.marketplace.domain.CarWashBusinessRepository;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketplaceWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired CarWashBusinessRepository businesses;
    @Autowired CarWashBranchRepository branches;

    @Test
    void businessAndBranchManagementLifecyclePreservesIdentityOwnershipAndDiscoveryRules() throws Exception {
        CreateBusinessRequest business = validBusiness();
        CreateBranchRequest branch = validBranch();

        api.createBusiness(business)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(business.businessId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.registeredAt").exists());
        api.createBranch(business.businessId(), branch)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchId").value(branch.branchId()))
                .andExpect(jsonPath("$.businessId").value(business.businessId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.effectiveActive").value(true))
                .andExpect(jsonPath("$.discoverable").value(true));

        mockMvc.perform(get("/api/marketplace/businesses/{id}/branches", business.businessId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branchId").value(branch.branchId()));
        mockMvc.perform(get("/api/marketplace/branches/discoverable")
                        .with(authentication.roleJwt("customer", "CUSTOMER", "ROLE_CUSTOMER",
                                "PERM_MARKETPLACE_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branchId").value(branch.branchId()));

        UpdateBusinessRequest businessUpdate = new UpdateBusinessRequest(
                "Updated Wash Group", "updated@example.test", "+27 21 555 0100", "REG-UPDATED");
        mockMvc.perform(put("/api/marketplace/businesses/{id}", business.businessId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(businessUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(business.businessId()))
                .andExpect(jsonPath("$.businessName").value("Updated Wash Group"));

        UpdateBranchRequest branchUpdate = new UpdateBranchRequest(
                "Waterfront Branch", "2 Dock Road", "Unit 5", "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.908), BigDecimal.valueOf(18.42), "Africa/Johannesburg", true);
        mockMvc.perform(put("/api/marketplace/branches/{id}", branch.branchId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(branchUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchId").value(branch.branchId()))
                .andExpect(jsonPath("$.businessId").value(business.businessId()))
                .andExpect(jsonPath("$.branchName").value("Waterfront Branch"));

        mockMvc.perform(post("/api/marketplace/businesses/{id}/deactivate", business.businessId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(get("/api/marketplace/branches/{id}", branch.branchId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.effectiveActive").value(false))
                .andExpect(jsonPath("$.discoverable").value(false));
        mockMvc.perform(get("/api/marketplace/branches/discoverable")
                .with(authentication.roleJwt("customer", "CUSTOMER", "ROLE_CUSTOMER",
                                "PERM_MARKETPLACE_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/marketplace/businesses/{id}/activate", business.businessId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/api/marketplace/branches/{id}/deactivate", branch.branchId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(post("/api/marketplace/branches/{id}/activate", branch.branchId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.discoverable").value(true));

        assertEquals(1, businesses.findAll().size());
        assertEquals(1, branches.findAll().size());
        assertEquals(business.businessId(), branches.findById(branch.branchId()).orElseThrow().getBusinessId());
    }

    @Test
    void duplicateIdentifiersAndUnknownParentsUseStandardErrorContract() throws Exception {
        CreateBusinessRequest business = validBusiness();
        CreateBranchRequest branch = validBranch();
        api.createBusiness(business).andExpect(status().isCreated());
        api.createBusiness(business)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Business ID already exists"));

        api.createBranch(business.businessId(), branch).andExpect(status().isCreated());
        api.createBranch(business.businessId(), branch)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Branch ID already exists"));

        api.createBranch("missing-business", new CreateBranchRequest(
                        ids.branch(), branch.branchName(), branch.addressLine1(), branch.addressLine2(), branch.city(),
                        branch.province(), branch.postalCode(), branch.countryCode(), branch.latitude(), branch.longitude(),
                        branch.timezone(), branch.publicDiscoveryEnabled()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/marketplace/businesses/missing-business/branches"));

        mockMvc.perform(get("/api/marketplace/branches/{id}", "missing-branch")
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void coordinateAndRequestValidationPrecedeMutationAndTimezoneUsesBusinessRuleContract() throws Exception {
        CreateBusinessRequest business = validBusiness();
        api.createBusiness(business).andExpect(status().isCreated());

        CreateBranchRequest invalidLatitude = new CreateBranchRequest(
                ids.branch(), "Invalid Coordinates", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(91), BigDecimal.valueOf(18.42), "Africa/Johannesburg", true);
        api.createBranch(business.businessId(), invalidLatitude)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("latitude"));

        CreateBranchRequest invalidTimezone = new CreateBranchRequest(
                ids.branch(), "Invalid Timezone", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.9), BigDecimal.valueOf(18.42), "Africa/Unknown", true);
        api.createBranch(business.businessId(), invalidTimezone)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Timezone identifier is invalid"));

        CreateBusinessRequest blankBusiness = new CreateBusinessRequest(
                " ", " ", "invalid", "bad", null);
        api.createBusiness(blankBusiness)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        assertTrue(branches.findAll().isEmpty());
        assertEquals(1, businesses.findAll().size());
    }

    @Test
    void marketplaceResponsesRemainBoundedAndDoNotExposeAggregateGraphs() throws Exception {
        CreateBusinessRequest business = validBusiness();
        CreateBranchRequest branch = validBranch();
        api.createBusiness(business).andExpect(status().isCreated());
        api.createBranch(business.businessId(), branch).andExpect(status().isCreated());

        String businessBody = mockMvc.perform(get("/api/marketplace/businesses/{id}", business.businessId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String branchBody = mockMvc.perform(get("/api/marketplace/branches/{id}", branch.branchId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(!businessBody.contains("branches") && !businessBody.contains("repository"));
        assertTrue(!branchBody.contains("businessName") && !branchBody.contains("repository"));
    }

    private CreateBusinessRequest validBusiness() {
        return new CreateBusinessRequest(
                ids.business(), "Marketplace Wash Group", "marketplace@example.test", "+27 82 123 4567", "REG-001");
    }

    private CreateBranchRequest validBranch() {
        return new CreateBranchRequest(
                ids.branch(), "City Bowl Branch", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241), "Africa/Johannesburg", true);
    }
}
