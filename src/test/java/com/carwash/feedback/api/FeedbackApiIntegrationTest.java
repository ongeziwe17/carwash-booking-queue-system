package com.carwash.feedback.api;

import com.carwash.booking.domain.*;
import com.carwash.feedback.domain.FeedbackRepository;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.testsupport.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FeedbackApiIntegrationTest extends ApiIntegrationTestSupport {
    @Autowired BookingRepository bookings;
    @Autowired DataTransactionOperations tx;
    @Autowired FeedbackRepository feedback;

    @Test void verifiedHttpWorkflowRetainsPrivacyTotalsAndModeration() throws Exception {
        var fixture = new BookingApiFixture(api,ids).createBooking(TestDates.futureDays(2));
        var resources = fixture.resources(); String bookingId = fixture.booking().bookingId();
        var customer = authentication.customerJwt(resources.user().userId());
        var owner = authentication.tenantRoleJwt(resources.user().userId(),"BUSINESS_OWNER",
                resources.business().businessId(),"ROLE_BUSINESS_OWNER","PERM_MARKETPLACE_MANAGE");
        var admin = authentication.platformAdminJwt();
        String body = "{\"bookingId\":\"" + bookingId + "\",\"rating\":5,\"comment\":\"Great <b>wash</b>\"}";
        mockMvc.perform(post("/api/feedback/reviews").with(customer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        tx.write(() -> {
            Booking booking = bookings.findById(bookingId).orElseThrow();
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setScheduledDateTime(LocalDateTime.of(2020,1,1,10,0));
            assertTrue(bookings.update(booking));
        });
        mockMvc.perform(post("/api/feedback/reviews").with(authentication.customerJwt("foreign"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/feedback/reviews").with(customer).contentType(MediaType.APPLICATION_JSON)
                .content(body.substring(0,body.length()-1)+",\"businessId\":\"other\"}"))
                .andExpect(status().isBadRequest());
        String reviewJson = mockMvc.perform(post("/api/feedback/reviews").with(customer)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5)).andReturn().getResponse().getContentAsString();
        String reviewId = objectMapper.readTree(reviewJson).get("id").asString();
        mockMvc.perform(post("/api/feedback/reviews").with(customer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
        mockMvc.perform(get("/api/feedback/bookings/{bookingId}/review",bookingId).with(customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comment").value("Great <b>wash</b>"));
        mockMvc.perform(get("/api/feedback/bookings/{bookingId}/review",bookingId)
                .with(authentication.customerJwt("foreign"))).andExpect(status().isNotFound());
        String tenant = resources.business().businessId();
        mockMvc.perform(get("/api/feedback/businesses/{businessId}/reviews",tenant).with(owner)
                .param("rating","5").param("page","8").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/api/feedback/businesses/{businessId}/reviews","foreign").with(owner))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/feedback/branches/{branchId}/summary",resources.branch().branchId()).with(customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.distribution[4]").value(1))
                .andExpect(jsonPath("$.customerId").doesNotExist());
        mockMvc.perform(get("/api/feedback/offerings/{offeringId}/summary",resources.offering().offeringId()).with(customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.averageRating").value(5.00));
        String report = mockMvc.perform(post("/api/feedback/reviews/{reviewId}/reports",reviewId).with(owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Concern\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String reportId = objectMapper.readTree(report).get("id").asString();
        mockMvc.perform(get("/api/feedback/branches/{branchId}/summary",resources.branch().branchId()).with(customer))
                .andExpect(jsonPath("$.reviewCount").value(1));
        mockMvc.perform(post("/api/feedback/reviews/{reviewId}/hide",reviewId).with(owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/feedback/reviews/{reviewId}/hide",reviewId).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Investigated\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/feedback/branches/{branchId}/summary",resources.branch().branchId()).with(customer))
                .andExpect(jsonPath("$.reviewCount").value(0)).andExpect(jsonPath("$.averageRating").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/api/feedback/businesses/{businessId}/reports",tenant).with(admin)
                .param("state","OPEN")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(post("/api/feedback/reports/{reportId}/resolve",reportId).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Resolved\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feedback/reviews/{reviewId}/restore",reviewId).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Restored\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/feedback/branches/{branchId}/summary",resources.branch().branchId()).with(customer))
                .andExpect(jsonPath("$.reviewCount").value(1));
    }
}
