package com.carwash.marketplace.api;

import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketplaceAuthorizationIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void businessOwnerAndPlatformAdminCanManageMarketplace() throws Exception {
        CreateBusinessRequest ownerBusiness = business(ids.business());
        mockMvc.perform(post("/api/marketplace/businesses")
                        .with(role("owner", "BUSINESS_OWNER", "PERM_MARKETPLACE_READ", "PERM_MARKETPLACE_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerBusiness)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/marketplace/businesses/{id}", ownerBusiness.businessId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(ownerBusiness.businessId()));
    }

    @Test
    void customerAndStaffCanReadDiscoveryButCannotManageMarketplace() throws Exception {
        api.createBusiness(business(ids.business())).andExpect(status().isCreated());

        for (RequestPostProcessor reader : java.util.List.of(
                role("customer", "CUSTOMER", "PERM_MARKETPLACE_READ"),
                role("staff", "STAFF", "PERM_MARKETPLACE_READ")
        )) {
            mockMvc.perform(get("/api/marketplace/branches/discoverable").with(reader))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/marketplace/businesses").with(reader))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            mockMvc.perform(post("/api/marketplace/businesses").with(reader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(business(ids.business()))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void unauthenticatedMarketplaceRequestsReturnStandard401() throws Exception {
        mockMvc.perform(get("/api/marketplace/branches/discoverable"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(post("/api/marketplace/businesses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(business(ids.business()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private RequestPostProcessor role(String subject, String role, String... permissions) {
        String[] authorities = new String[permissions.length + 1];
        authorities[0] = "ROLE_" + role;
        System.arraycopy(permissions, 0, authorities, 1, permissions.length);
        return authentication.roleJwt(subject, role, authorities);
    }

    private CreateBusinessRequest business(String businessId) {
        return new CreateBusinessRequest(
                businessId, "Authorized Wash Group", "owner@example.test", "+27 82 123 4567", "REG-AUTH");
    }
}
