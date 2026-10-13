package com.carwash.feedback.api;

import com.carwash.booking.domain.*;
import com.carwash.feedback.domain.FeedbackRepository;
import com.carwash.audit.domain.*;
import com.carwash.identity.application.*;
import com.carwash.identity.domain.*;
import com.carwash.notification.domain.*;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.testsupport.*;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
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
    @Autowired AuditRepository auditRecords;
    @Autowired UserManagementService users;
    @Autowired UserRepository userRepository;
    @Autowired TenantMembershipManagementService memberships;
    @Autowired TenantMembershipRepository membershipRepository;
    @Autowired NotificationRepository notifications;

    @Test void verifiedHttpWorkflowRetainsPrivacyTotalsAndModeration() throws Exception {
        var fixture = new BookingApiFixture(api,ids).createBooking(TestDates.futureDays(2));
        var resources = fixture.resources(); String bookingId = fixture.booking().bookingId();
        var customer = authentication.customerJwt(resources.user().userId());
        var owner = authentication.tenantRoleJwt(resources.user().userId(),"BUSINESS_OWNER",
                resources.business().businessId(),"ROLE_BUSINESS_OWNER","PERM_MARKETPLACE_MANAGE");
        var admin = authentication.platformAdminJwt();
        users.createUser(new CreateUserCommand("test-platform-admin", "Test Administrator",
                "test-platform-admin@example.test", "0800000000", UserFixtureBuilder.DEFAULT_PASSWORD));
        users.assignRole("test-platform-admin", RoleName.PLATFORM_ADMIN);
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
        String foreignBusiness = ids.business();
        api.createBusiness(new com.carwash.marketplace.api.dto.CreateBusinessRequest(foreignBusiness,
                "Other business",ids.emailFor(foreignBusiness),"0821234567",null))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/feedback/reviews/{reviewId}/reports",reviewId)
                .with(authentication.tenantRoleJwt(resources.user().userId(),"BUSINESS_OWNER",foreignBusiness,
                        "ROLE_BUSINESS_OWNER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Foreign\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/feedback/reviews/{reviewId}/reports","missing").with(owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Missing\"}"))
                .andExpect(status().isNotFound());
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
        var events = auditRecords.queryByBusinessId(tenant, new AuditQuery(null,null,AuditOutcome.SUCCESS,
                null,null,Instant.EPOCH,Instant.now().plusSeconds(60),null,100)).stream()
                .filter(record -> record.action().name().startsWith("FEEDBACK_")).toList();
        assertEquals(List.of(AuditAction.FEEDBACK_SUBMITTED, AuditAction.FEEDBACK_REPORTED,
                AuditAction.FEEDBACK_HIDDEN, AuditAction.FEEDBACK_REPORT_RESOLVED, AuditAction.FEEDBACK_RESTORED).stream().sorted().toList(),
                events.stream().map(AuditRecord::action).sorted().toList());
        assertTrue(events.stream().allMatch(record -> record.businessId().equals(tenant)
                && record.metadata().isEmpty()));
        assertTrue(auditRecords.queryByBusinessId(foreignBusiness,new AuditQuery(null,null,null,
                null,null,Instant.EPOCH,Instant.now().plusSeconds(60),null,100)).stream()
                .noneMatch(record -> record.action().name().startsWith("FEEDBACK_")));
        assertTrue(auditRecords.queryPlatform(new AuditQuery(null,AuditAction.FEEDBACK_REPORTED,
                AuditOutcome.DENIED,"REVIEW",reviewId,Instant.EPOCH,Instant.now().plusSeconds(60),null,100))
                .stream().anyMatch(record -> record.businessId() == null));
        mockMvc.perform(get("/api/audit-records").with(authentication.tenantRoleJwt("owner", "BUSINESS_OWNER",
                tenant,"ROLE_BUSINESS_OWNER","PERM_AUDIT_READ")).param("action","FEEDBACK_REPORTED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records[0].businessId").value(tenant));
        mockMvc.perform(get("/api/audit-records").with(admin).param("scope","TENANT")
                .param("businessId",tenant).param("action","FEEDBACK_HIDDEN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records[0].businessId").value(tenant));
    }

    @Test void reportedOwnerDeletionIsGuardedBeforeMembershipAndNotificationCleanup() throws Exception {
        var fixture = new BookingApiFixture(api,ids).createBooking(TestDates.futureDays(2));
        var resources = fixture.resources(); String tenant = resources.business().businessId();
        String ownerId = ids.user();
        var ownerRequest = UserFixtureBuilder.valid(ids).userId(ownerId).email(ids.emailFor(ownerId)).build();
        api.createUser(ownerRequest).andExpect(status().isCreated());
        memberships.assignRole(ownerId,RoleName.BUSINESS_OWNER,tenant);
        var owner = authentication.tenantRoleJwt(ownerId,"BUSINESS_OWNER",tenant,
                "ROLE_BUSINESS_OWNER","PERM_MARKETPLACE_MANAGE");
        Notification notice = new Notification(ids.notification(),userRepository.findById(ownerId).orElseThrow(),
                null,"SYSTEM","Retain account notification","IN_APP");
        tx.write(() -> assertTrue(notifications.insert(notice)));
        String bookingId = fixture.booking().bookingId();
        tx.write(() -> {
            Booking booking = bookings.findById(bookingId).orElseThrow();
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setScheduledDateTime(LocalDateTime.of(2020,1,1,10,0));
            assertTrue(bookings.update(booking));
        });
        String reviewJson = mockMvc.perform(post("/api/feedback/reviews")
                .with(authentication.customerJwt(resources.user().userId())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":\""+bookingId+"\",\"rating\":5}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String reviewId = objectMapper.readTree(reviewJson).get("id").asString();
        mockMvc.perform(post("/api/feedback/reviews/{id}/reports",reviewId).with(owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Concern\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/users/{id}",ownerId).with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        assertTrue(userRepository.findById(ownerId).isPresent());
        assertEquals(tenant,membershipRepository.findById(ownerId).orElseThrow().businessId());
        assertEquals(1,notifications.findByUserId(ownerId).size());
        String freeId = ids.user();
        api.createUser(UserFixtureBuilder.valid(ids).userId(freeId).email(ids.emailFor(freeId)).build())
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/users/{id}",freeId).with(authentication.platformAdminJwt()))
                .andExpect(status().isNoContent());
        assertTrue(userRepository.findById(freeId).isEmpty());
    }
}
