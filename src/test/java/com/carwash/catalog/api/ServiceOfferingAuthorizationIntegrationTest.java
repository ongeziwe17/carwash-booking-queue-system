package com.carwash.catalog.api;

import com.carwash.catalog.api.dto.CreateServiceOfferingRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.catalog.api.dto.UpdateServiceOfferingRequest;
import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ServiceOfferingAuthorizationIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void ownerAndAdministratorCanManageOfferings() throws Exception {
        Fixture fixture = createFixture();
        RequestPostProcessor owner = role(
                "owner", "BUSINESS_OWNER", "PERM_MARKETPLACE_READ", "PERM_MARKETPLACE_MANAGE");
        CreateServiceOfferingRequest request = request(ids.offering(), fixture.serviceId());

        create(fixture.branchId(), request, owner).andExpect(status().isCreated());
        mockMvc.perform(get("/api/marketplace/branches/{branchId}/offerings", fixture.branchId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/marketplace/offerings/{offeringId}", request.offeringId()).with(owner))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/marketplace/offerings/{offeringId}", request.offeringId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateServiceOfferingRequest(BigDecimal.valueOf(120), 45, 4))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/marketplace/offerings/{offeringId}/deactivate", request.offeringId()).with(owner))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/marketplace/offerings/{offeringId}/activate", request.offeringId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void customerAndStaffCanDiscoverButCannotUseAnyManagementOperation() throws Exception {
        Fixture fixture = createFixture();
        CreateServiceOfferingRequest existing = request(ids.offering(), fixture.serviceId());
        create(fixture.branchId(), existing, authentication.platformAdminJwt()).andExpect(status().isCreated());

        for (RequestPostProcessor reader : List.of(
                role("customer", "CUSTOMER", "PERM_MARKETPLACE_READ"),
                role("staff", "STAFF", "PERM_MARKETPLACE_READ")
        )) {
            mockMvc.perform(get("/api/marketplace/branches/{branchId}/offerings/discoverable", fixture.branchId())
                            .with(reader))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/marketplace/branches/{branchId}/offerings", fixture.branchId()).with(reader))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            create(fixture.branchId(), request(ids.offering(), fixture.serviceId()), reader)
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/marketplace/offerings/{offeringId}", existing.offeringId()).with(reader))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/marketplace/offerings/{offeringId}", existing.offeringId())
                            .with(reader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateServiceOfferingRequest(BigDecimal.TEN, 30, 2))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/marketplace/offerings/{offeringId}/activate", existing.offeringId()).with(reader))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/marketplace/offerings/{offeringId}/deactivate", existing.offeringId()).with(reader))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void allSevenOfferingOperationsRejectUnauthenticatedRequestsWithStandard401() throws Exception {
        Fixture fixture = createFixture();
        CreateServiceOfferingRequest existing = request(ids.offering(), fixture.serviceId());
        create(fixture.branchId(), existing, authentication.platformAdminJwt()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/marketplace/branches/{branchId}/offerings", fixture.branchId()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        create(fixture.branchId(), request(ids.offering(), fixture.serviceId()), request -> request)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/marketplace/offerings/{offeringId}", existing.offeringId()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(put("/api/marketplace/offerings/{offeringId}", existing.offeringId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateServiceOfferingRequest(BigDecimal.TEN, 30, 2))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(post("/api/marketplace/offerings/{offeringId}/activate", existing.offeringId()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(post("/api/marketplace/offerings/{offeringId}/deactivate", existing.offeringId()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/marketplace/branches/{branchId}/offerings/discoverable", fixture.branchId()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private Fixture createFixture() throws Exception {
        String businessId = ids.business();
        String branchId = ids.branch();
        String serviceId = ids.service();
        api.createBusiness(new CreateBusinessRequest(
                businessId, "Wash Group", "owner@example.test", "+27 82 123 4567", "REG-001"))
                .andExpect(status().isCreated());
        api.createBranch(businessId, new CreateBranchRequest(
                branchId, "Branch", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241), "Africa/Johannesburg", true))
                .andExpect(status().isCreated());
        api.createService(new CreateServiceRequest(
                serviceId, "Exterior", "Reusable definition", BigDecimal.valueOf(80), 20))
                .andExpect(status().isCreated());
        return new Fixture(branchId, serviceId);
    }

    private org.springframework.test.web.servlet.ResultActions create(
            String branchId,
            CreateServiceOfferingRequest request,
            RequestPostProcessor authorization
    ) throws Exception {
        return mockMvc.perform(post("/api/marketplace/branches/{branchId}/offerings", branchId)
                .with(authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private CreateServiceOfferingRequest request(String offeringId, String serviceId) {
        return new CreateServiceOfferingRequest(offeringId, serviceId, BigDecimal.valueOf(100), 30, 2);
    }

    private RequestPostProcessor role(String subject, String role, String... permissions) {
        String[] authorities = new String[permissions.length + 1];
        authorities[0] = "ROLE_" + role;
        System.arraycopy(permissions, 0, authorities, 1, permissions.length);
        return authentication.roleJwt(subject, role, authorities);
    }

    private record Fixture(String branchId, String serviceId) {
    }
}
