package com.carwash.marketplace.api;

import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.marketplace.api.dto.CreateTemporaryClosureRequest;
import com.carwash.marketplace.api.dto.ReplaceOperatingHoursRequest;
import com.carwash.marketplace.api.dto.WeeklyOperatingIntervalRequest;
import com.carwash.marketplace.domain.BranchOperatingScheduleRepository;
import com.carwash.marketplace.domain.TemporaryBranchClosureRepository;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BranchSchedulingWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired BranchOperatingScheduleRepository schedules;
    @Autowired TemporaryBranchClosureRepository closures;

    @Test
    void weeklyHoursClosureCancellationAndOvernightFlowUsesBranchTimezone() throws Exception {
        String branchId = createBranch("Africa/Johannesburg");
        ReplaceOperatingHoursRequest hours = new ReplaceOperatingHoursRequest(List.of(
                interval(DayOfWeek.MONDAY, "08:00", "17:00"),
                interval(DayOfWeek.FRIDAY, "20:00", "02:00")
        ));

        replaceHours(branchId, hours)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchId").value(branchId))
                .andExpect(jsonPath("$.timezone").value("Africa/Johannesburg"))
                .andExpect(jsonPath("$.intervals.length()").value(2))
                .andExpect(jsonPath("$.createdAt").exists());
        mockMvc.perform(get("/api/marketplace/branches/{branchId}/operating-hours", branchId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervals[0].dayOfWeek").value("MONDAY"));

        String normalAt = "2030-01-07T10:00:00+02:00";
        openStatus(branchId, normalAt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedAt").value("2030-01-07T08:00:00Z"))
                .andExpect(jsonPath("$.timezone").value("Africa/Johannesburg"))
                .andExpect(jsonPath("$.branchLocalDateTime", Matchers.containsString("+02:00")))
                .andExpect(jsonPath("$.effectiveActive").value(true))
                .andExpect(jsonPath("$.withinWeeklyHours").value(true))
                .andExpect(jsonPath("$.temporarilyClosed").value(false))
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.applicableClosureId").value(Matchers.nullValue()));

        String closureId = ids.closure();
        CreateTemporaryClosureRequest closure = new CreateTemporaryClosureRequest(
                closureId,
                OffsetDateTime.parse("2030-01-07T09:30:00+02:00"),
                OffsetDateTime.parse("2030-01-07T10:30:00+02:00"),
                "Water maintenance");
        createClosure(branchId, closure)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.closureId").value(closureId))
                .andExpect(jsonPath("$.branchId").value(branchId))
                .andExpect(jsonPath("$.startAt").value("2030-01-07T07:30:00Z"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        openStatus(branchId, normalAt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporarilyClosed").value(true))
                .andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.applicableClosureId").value(closureId))
                .andExpect(jsonPath("$.applicableClosureReason").value("Water maintenance"));
        mockMvc.perform(get("/api/marketplace/branches/{branchId}/closures", branchId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mockMvc.perform(post("/api/marketplace/closures/{closureId}/cancel", closureId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        openStatus(branchId, normalAt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporarilyClosed").value(false))
                .andExpect(jsonPath("$.open").value(true));
        openStatus(branchId, "2030-01-12T01:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withinWeeklyHours").value(true))
                .andExpect(jsonPath("$.open").value(true));

        assertEquals(1, schedules.findAll().size());
        assertEquals(1, closures.findAll().size());
    }

    @Test
    void scheduleAndClosureValidationUseStandardErrorsWithoutPartialMutation() throws Exception {
        String branchId = createBranch("Africa/Johannesburg");
        ReplaceOperatingHoursRequest overlapping = new ReplaceOperatingHoursRequest(List.of(
                interval(DayOfWeek.MONDAY, "08:00", "12:00"),
                interval(DayOfWeek.MONDAY, "11:00", "17:00")
        ));
        replaceHours(branchId, overlapping)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Weekly operating intervals must not overlap"));
        replaceHours(branchId, new ReplaceOperatingHoursRequest(List.of(
                interval(DayOfWeek.MONDAY, "08:00", "08:00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        replaceHours(branchId, new ReplaceOperatingHoursRequest(List.of(
                interval(DayOfWeek.SUNDAY, "20:00", "02:00"),
                interval(DayOfWeek.MONDAY, "01:00", "06:00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Weekly operating intervals must not overlap"));
        assertTrue(schedules.findAll().isEmpty());

        CreateTemporaryClosureRequest invalidRange = new CreateTemporaryClosureRequest(
                ids.closure(),
                OffsetDateTime.parse("2030-01-07T10:00:00+02:00"),
                OffsetDateTime.parse("2030-01-07T09:00:00+02:00"),
                "Invalid range");
        createClosure(branchId, invalidRange)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Closure start must be before closure end"));

        String closureId = ids.closure();
        CreateTemporaryClosureRequest valid = new CreateTemporaryClosureRequest(
                closureId,
                OffsetDateTime.parse("2030-01-07T09:00:00+02:00"),
                OffsetDateTime.parse("2030-01-07T11:00:00+02:00"),
                "Maintenance");
        createClosure(branchId, valid).andExpect(status().isCreated());
        createClosure(branchId, valid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Closure ID already exists"));
        createClosure(branchId, new CreateTemporaryClosureRequest(
                        ids.closure(),
                        OffsetDateTime.parse("2030-01-07T10:00:00+02:00"),
                        OffsetDateTime.parse("2030-01-07T12:00:00+02:00"),
                        "Overlap"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Active temporary closures must not overlap"));
        assertEquals(1, closures.findAll().size());
    }

    @Test
    void malformedInputsAndMissingResourcesUseExisting400And404Contracts() throws Exception {
        String branchId = createBranch("America/New_York");

        mockMvc.perform(put("/api/marketplace/branches/{branchId}/operating-hours", branchId)
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("intervals", List.of(Map.of(
                                "dayOfWeek", "NOT_A_DAY", "opensAt", "08:00", "closesAt", "17:00"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(get("/api/marketplace/branches/{branchId}/open-status", branchId)
                        .with(authentication.platformAdminJwt())
                        .param("at", "not-a-timestamp"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/marketplace/branches/{branchId}/open-status", branchId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
        mockMvc.perform(get("/api/marketplace/branches/{branchId}/operating-hours", "missing")
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/marketplace/closures/{closureId}/cancel", "missing")
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private String createBranch(String timezone) throws Exception {
        CreateBusinessRequest business = new CreateBusinessRequest(
                ids.business(), "Schedule Wash Group", "schedule@example.test", "+27 82 123 4567", "REG-SCHEDULE");
        CreateBranchRequest branch = new CreateBranchRequest(
                ids.branch(), "Schedule Branch", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241), timezone, true);
        api.createBusiness(business).andExpect(status().isCreated());
        api.createBranch(business.businessId(), branch).andExpect(status().isCreated());
        return branch.branchId();
    }

    private WeeklyOperatingIntervalRequest interval(DayOfWeek day, String opensAt, String closesAt) {
        return new WeeklyOperatingIntervalRequest(day, LocalTime.parse(opensAt), LocalTime.parse(closesAt));
    }

    private org.springframework.test.web.servlet.ResultActions replaceHours(
            String branchId,
            ReplaceOperatingHoursRequest request
    ) throws Exception {
        return mockMvc.perform(put("/api/marketplace/branches/{branchId}/operating-hours", branchId)
                .with(authentication.platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions createClosure(
            String branchId,
            CreateTemporaryClosureRequest request
    ) throws Exception {
        return mockMvc.perform(post("/api/marketplace/branches/{branchId}/closures", branchId)
                .with(authentication.platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private org.springframework.test.web.servlet.ResultActions openStatus(String branchId, String at)
            throws Exception {
        return mockMvc.perform(get("/api/marketplace/branches/{branchId}/open-status", branchId)
                .with(authentication.roleJwt(
                        "customer", "CUSTOMER", "ROLE_CUSTOMER", "PERM_MARKETPLACE_READ"))
                .param("at", at));
    }
}
