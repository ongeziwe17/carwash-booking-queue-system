package com.carwash.workflow;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.catalog.api.dto.CreateServiceOfferingRequest;
import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.ReplaceOperatingHoursRequest;
import com.carwash.marketplace.api.dto.WeeklyOperatingIntervalRequest;
import com.carwash.queue.api.dto.CreateQueueEntryRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingApiFixture;
import com.carwash.testsupport.BookingFixtureBuilder;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BranchScopedOperationsWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void branchScopedBookingQueueNotificationAndReportWorkflowIsIsolated() throws Exception {
        LocalDateTime scheduled = TestDates.futureDays(120);
        BookingApiFixture.CreatedBooking branchABooking = new BookingApiFixture(api, ids).createBooking(scheduled);
        BookingApiFixture.Resources resources = branchABooking.resources();

        CreateBranchRequest branchB = new CreateBranchRequest(
                ids.branch(), "Second Operations Branch", "2 Test Street", null, "Cape Town", "Western Cape",
                "8001", "ZA", new BigDecimal("-33.9250"), new BigDecimal("18.4250"),
                "Africa/Johannesburg", true);
        api.createBranch(resources.business().businessId(), branchB).andExpect(status().isCreated());
        api.replaceOperatingHours(branchB.branchId(), new ReplaceOperatingHoursRequest(java.util.List.of(
                new WeeklyOperatingIntervalRequest(scheduled.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(17, 0))
        ))).andExpect(status().isOk());
        CreateServiceOfferingRequest offeringB = new CreateServiceOfferingRequest(
                ids.offering(), resources.service().serviceId(), BigDecimal.valueOf(275), 45, 3);
        api.createServiceOffering(branchB.branchId(), offeringB).andExpect(status().isCreated());

        CreateBookingRequest branchBRequest = BookingFixtureBuilder.valid(
                        ids, resources.user().userId(), resources.vehicle().vehicleId(),
                        branchB.branchId(), offeringB.offeringId())
                .scheduledDateTime(scheduled)
                .specialRequest("second branch")
                .build();
        api.createBooking(branchBRequest)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchId").value(branchB.branchId()))
                .andExpect(jsonPath("$.serviceOfferingId").value(offeringB.offeringId()))
                .andExpect(jsonPath("$.service.serviceId").value(resources.service().serviceId()));

        mockMvc.perform(get("/api/bookings")
                        .with(authentication.platformAdminJwt())
                        .param("branchId", resources.branch().branchId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].branchId").value(resources.branch().branchId()))
                .andExpect(jsonPath("$[0].serviceOfferingId").value(resources.offering().offeringId()));
        mockMvc.perform(get("/api/bookings")
                        .with(authentication.platformAdminJwt())
                        .param("branchId", "missing-branch"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(put("/api/bookings/{id}", branchABooking.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", resources.vehicle().vehicleId(),
                                "serviceOfferingId", offeringB.offeringId(),
                                "specialRequest", "cannot move branches"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        confirm(branchABooking.booking().bookingId());
        confirm(branchBRequest.bookingId());
        CreateQueueEntryRequest queueA = new CreateQueueEntryRequest(
                ids.queueEntry(), branchABooking.booking().bookingId(), resources.service().serviceId());
        CreateQueueEntryRequest queueB = new CreateQueueEntryRequest(
                ids.queueEntry(), branchBRequest.bookingId(), resources.service().serviceId());
        api.createQueueEntry(queueA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchId").value(resources.branch().branchId()))
                .andExpect(jsonPath("$.serviceOfferingId").value(resources.offering().offeringId()))
                .andExpect(jsonPath("$.position").value(1));
        api.createQueueEntry(queueB)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchId").value(branchB.branchId()))
                .andExpect(jsonPath("$.serviceOfferingId").value(offeringB.offeringId()))
                .andExpect(jsonPath("$.position").value(1));

        mockMvc.perform(post("/api/queue-entries/call-next")
                        .with(authentication.platformAdminJwt())
                        .param("branchId", branchB.branchId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueEntryId").value(queueB.queueEntryId()))
                .andExpect(jsonPath("$.branchId").value(branchB.branchId()));
        mockMvc.perform(get("/api/queue-entries")
                        .with(authentication.platformAdminJwt())
                        .param("branchId", resources.branch().branchId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].queueEntryId").value(queueA.queueEntryId()))
                .andExpect(jsonPath("$[0].queueStatus").value("WAITING"));

        mockMvc.perform(get("/api/notifications/user/{userId}", resources.user().userId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(resources.user().userId()))
                .andExpect(jsonPath("$[0].branchId").isString())
                .andExpect(jsonPath("$[0].serviceOfferingId").isString())
                .andExpect(jsonPath("$[0].user").doesNotExist())
                .andExpect(jsonPath("$[0].booking").doesNotExist());

        mockMvc.perform(get("/api/reports/daily-summary")
                        .with(authentication.platformAdminJwt())
                        .param("date", scheduled.toLocalDate().toString())
                        .param("branchId", resources.branch().branchId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeType").value("BRANCH"))
                .andExpect(jsonPath("$.scopeId").value(resources.branch().branchId()))
                .andExpect(jsonPath("$.timezone").value("Africa/Johannesburg"))
                .andExpect(jsonPath("$.totalBookings").value(1))
                .andExpect(jsonPath("$.totalQueueEntries").value(1));
        mockMvc.perform(get("/api/reports/daily-summary")
                        .with(authentication.platformAdminJwt())
                        .param("date", scheduled.toLocalDate().toString())
                        .param("businessId", resources.business().businessId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeType").value("BUSINESS"))
                .andExpect(jsonPath("$.totalBookings").value(2))
                .andExpect(jsonPath("$.totalQueueEntries").value(2));

        mockMvc.perform(get("/api/reports/daily-summary")
                        .with(authentication.platformAdminJwt())
                        .param("date", scheduled.toLocalDate().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        mockMvc.perform(get("/api/reports/daily-summary")
                        .with(authentication.platformAdminJwt())
                        .param("date", scheduled.toLocalDate().toString())
                        .param("branchId", resources.branch().branchId())
                        .param("businessId", resources.business().businessId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void bookingCreationRejectsMissingAndCrossBranchScope() throws Exception {
        BookingApiFixture.Resources resources = new BookingApiFixture(api, ids).createResources();
        CreateBranchRequest branchB = new CreateBranchRequest(
                ids.branch(), "Validation Branch", "3 Test Street", null, "Cape Town", "Western Cape",
                "8001", "ZA", new BigDecimal("-33.9251"), new BigDecimal("18.4251"),
                "Africa/Johannesburg", true);
        api.createBranch(resources.business().businessId(), branchB).andExpect(status().isCreated());
        LocalDateTime scheduled = TestDates.futureDays(121);

        CreateBookingRequest crossBranch = BookingFixtureBuilder.valid(
                        ids, resources.user().userId(), resources.vehicle().vehicleId(),
                        branchB.branchId(), resources.offering().offeringId())
                .scheduledDateTime(scheduled)
                .build();
        api.createBooking(crossBranch)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Service offering does not belong to the requested branch"));

        String missingScopeBody = objectMapper.writeValueAsString(Map.of(
                "bookingId", ids.booking(),
                "userId", resources.user().userId(),
                "vehicleId", resources.vehicle().vehicleId(),
                "scheduledDateTime", scheduled,
                "specialRequest", "missing scope"));
        mockMvc.perform(post("/api/bookings")
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingScopeBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'branchId')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'serviceOfferingId')]").exists());
    }

    private void confirm(String bookingId) throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/confirm", bookingId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
    }
}
