package com.carwash.marketplace.api;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BranchSchedulingAuthorizationIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void businessOwnerAndPlatformAdminCanManageBranchScheduling() throws Exception {
        String branchId = createBranch();
        String closureId = ids.closure();
        RequestPostProcessor owner = role(
                "owner", "BUSINESS_OWNER", "PERM_MARKETPLACE_READ", "PERM_MARKETPLACE_MANAGE");

        replaceHours(branchId, owner).andExpect(status().isOk());
        mockMvc.perform(get("/api/marketplace/branches/{branchId}/operating-hours", branchId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        createClosure(branchId, closureId, owner).andExpect(status().isCreated());
        mockMvc.perform(get("/api/marketplace/branches/{branchId}/closures", branchId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/marketplace/closures/{closureId}/cancel", closureId).with(owner))
                .andExpect(status().isOk());
    }

    @Test
    void customerAndStaffCanReadOpenStatusButCannotManageScheduling() throws Exception {
        String branchId = createBranch();
        String closureId = ids.closure();
        replaceHours(branchId, authentication.platformAdminJwt()).andExpect(status().isOk());
        createClosure(branchId, closureId, authentication.platformAdminJwt()).andExpect(status().isCreated());

        for (RequestPostProcessor reader : List.of(
                role("customer", "CUSTOMER", "PERM_MARKETPLACE_READ"),
                role("staff", "STAFF", "PERM_MARKETPLACE_READ")
        )) {
            mockMvc.perform(get("/api/marketplace/branches/{branchId}/open-status", branchId)
                            .with(reader)
                            .param("at", "2030-01-07T10:00:00+02:00"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/marketplace/branches/{branchId}/operating-hours", branchId).with(reader))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            replaceHours(branchId, reader).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/marketplace/branches/{branchId}/closures", branchId).with(reader))
                    .andExpect(status().isForbidden());
            createClosure(branchId, ids.closure(), reader).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/marketplace/closures/{closureId}/cancel", closureId).with(reader))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void allSixSchedulingOperationsRejectUnauthenticatedRequestsWithStandard401() throws Exception {
        String branchId = createBranch();
        String closureId = ids.closure();
        createClosure(branchId, closureId, authentication.platformAdminJwt()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/marketplace/branches/{branchId}/operating-hours", branchId))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        replaceHours(branchId, request -> request)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/marketplace/branches/{branchId}/closures", branchId))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        createClosure(branchId, ids.closure(), request -> request)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(post("/api/marketplace/closures/{closureId}/cancel", closureId))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/marketplace/branches/{branchId}/open-status", branchId)
                        .param("at", "2030-01-07T10:00:00+02:00"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private String createBranch() throws Exception {
        CreateBusinessRequest business = new CreateBusinessRequest(
                ids.business(), "Authorized Wash Group", "owner@example.test", "+27 82 123 4567", "REG-AUTH");
        CreateBranchRequest branch = new CreateBranchRequest(
                ids.branch(), "Authorized Branch", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241), "Africa/Johannesburg", true);
        api.createBusiness(business).andExpect(status().isCreated());
        api.createBranch(business.businessId(), branch).andExpect(status().isCreated());
        return branch.branchId();
    }

    private org.springframework.test.web.servlet.ResultActions replaceHours(
            String branchId,
            RequestPostProcessor authorization
    ) throws Exception {
        ReplaceOperatingHoursRequest request = new ReplaceOperatingHoursRequest(List.of(
                new WeeklyOperatingIntervalRequest(
                        DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        return mockMvc.perform(put("/api/marketplace/branches/{branchId}/operating-hours", branchId)
                .with(authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions createClosure(
            String branchId,
            String closureId,
            RequestPostProcessor authorization
    ) throws Exception {
        CreateTemporaryClosureRequest request = new CreateTemporaryClosureRequest(
                closureId,
                OffsetDateTime.parse("2030-01-07T09:00:00+02:00"),
                OffsetDateTime.parse("2030-01-07T10:00:00+02:00"),
                "Maintenance");
        return mockMvc.perform(post("/api/marketplace/branches/{branchId}/closures", branchId)
                .with(authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private RequestPostProcessor role(String subject, String role, String... permissions) {
        String[] authorities = new String[permissions.length + 1];
        authorities[0] = "ROLE_" + role;
        System.arraycopy(permissions, 0, authorities, 1, permissions.length);
        return authentication.roleJwt(subject, role, authorities);
    }
}
