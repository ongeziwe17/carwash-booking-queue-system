package com.carwash.audit.api;

import com.carwash.audit.application.*;
import com.carwash.audit.domain.*;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import com.carwash.marketplace.application.MarketplaceManagementService;
import com.carwash.marketplace.application.RegisterBusinessCommand;
import com.carwash.marketplace.application.UpdateBusinessCommand;
import com.carwash.access.application.TenantAccessContext;
import com.carwash.identity.domain.RoleName;
import com.carwash.testsupport.TestAccess;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuditApiIntegrationTest extends ApiIntegrationTestSupport {
    @Autowired AuditOperations audit;
    @Autowired AuditRepository records;
    @Autowired MarketplaceManagementService marketplace;

    @Test void wiredSensitiveMutationCommitsOneRecordAndForeignSafe404UsesActorTenant() {
        marketplace.registerBusiness(TestAccess.platformAdministrator(), business("business-a"));
        marketplace.registerBusiness(TestAccess.platformAdministrator(), business("business-b"));
        TenantAccessContext ownerA = new TenantAccessContext("owner-a", RoleName.BUSINESS_OWNER, "business-a");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> marketplace.updateBusiness(ownerA, "business-b",
                new UpdateBusinessCommand("Forbidden", "business-b@example.test", "0123456789", "reg-business-b")))
                .isInstanceOf(com.carwash.shared.exception.ResourceNotFoundException.class);

        List<AuditRecord> ownerRecords = records.queryByBusinessId("business-a",
                new AuditQuery("owner-a", AuditAction.BUSINESS_UPDATED, null, "BUSINESS", "business-b",
                        Instant.EPOCH, Instant.now().plusSeconds(60), null, 10));
        org.assertj.core.api.Assertions.assertThat(ownerRecords).singleElement().satisfies(record -> {
            org.assertj.core.api.Assertions.assertThat(record.outcome()).isEqualTo(AuditOutcome.DENIED);
            org.assertj.core.api.Assertions.assertThat(record.reasonCode())
                    .isEqualTo("RESOURCE_NOT_FOUND_OR_FOREIGN");
            org.assertj.core.api.Assertions.assertThat(record.businessId()).isEqualTo("business-a");
        });
        org.assertj.core.api.Assertions.assertThat(records.queryByBusinessId("business-b",
                new AuditQuery("owner-a", AuditAction.BUSINESS_UPDATED, null, "BUSINESS", "business-b",
                        Instant.EPOCH, Instant.now().plusSeconds(60), null, 10))).isEmpty();
    }

    @Test void ownerReadsOnlyAuthenticatedTenantAndCannotOverrideIt() throws Exception {
        append("business-a", "target-a");
        append("business-b", "target-b");

        mockMvc.perform(get("/api/audit-records")
                        .with(authentication.tenantRoleJwt("owner-a", "BUSINESS_OWNER", "business-a",
                                "ROLE_BUSINESS_OWNER", "PERM_AUDIT_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[*].businessId", everyItem(is("business-a"))))
                .andExpect(jsonPath("$.records[?(@.targetId == 'target-a')]").exists())
                .andExpect(jsonPath("$.records[?(@.targetId == 'target-b')]").doesNotExist());

        mockMvc.perform(get("/api/audit-records").param("businessId", "business-b")
                        .with(authentication.tenantRoleJwt("owner-a", "BUSINESS_OWNER", "business-a",
                                "ROLE_BUSINESS_OWNER", "PERM_AUDIT_READ")))
                .andExpect(status().isForbidden());
    }

    @Test void staffAndCustomerCannotReadAuditHistory() throws Exception {
        mockMvc.perform(get("/api/audit-records")
                        .with(authentication.tenantRoleJwt("staff-a", "STAFF", "business-a",
                                "ROLE_STAFF")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audit-records")
                        .with(authentication.customerJwt("customer-a")))
                .andExpect(status().isForbidden());
    }

    @Test void administratorScopeIsExplicitAndNoWildcardExists() throws Exception {
        append("business-a", "target-a");
        append("business-b", "target-b");

        mockMvc.perform(get("/api/audit-records").with(authentication.platformAdminJwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audit-records").param("scope", "TENANT").param("businessId", "business-a")
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[*].businessId", everyItem(is("business-a"))));
        mockMvc.perform(get("/api/audit-records").param("scope", "PLATFORM")
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/audit-records").param("scope", "*")
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test void missingAndForgedBearerAreAuditedWithoutUntrustedIdentity() throws Exception {
        mockMvc.perform(get("/api/audit-records").param("scope", "PLATFORM"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/audit-records").param("scope", "PLATFORM")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer forged.header.claims"))
                .andExpect(status().isUnauthorized());

        List<AuditRecord> failures = platformRecords().stream()
                .filter(record -> record.action() == AuditAction.BEARER_AUTHENTICATION).toList();
        org.assertj.core.api.Assertions.assertThat(failures).hasSize(2)
                .allSatisfy(record -> {
                    org.assertj.core.api.Assertions.assertThat(record.actorType()).isEqualTo(AuditActorType.ANONYMOUS);
                    org.assertj.core.api.Assertions.assertThat(record.actorUserId()).isNull();
                    org.assertj.core.api.Assertions.assertThat(record.businessId()).isNull();
                    org.assertj.core.api.Assertions.assertThat(record.metadata().toString())
                            .doesNotContain("forged", "Bearer");
                });
    }

    @Test void invalidLoginIsAuditedWithoutAccountEnumerationOrCredentials() throws Exception {
        String password = "NeverPersistThisPassword123!";
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"absent@example.test\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized());

        org.assertj.core.api.Assertions.assertThat(platformRecords().stream()
                        .filter(record -> record.action() == AuditAction.LOGIN_FAILURE).toList())
                .singleElement().satisfies(record -> {
                    String serialized = record.toString();
                    org.assertj.core.api.Assertions.assertThat(record.actorType()).isEqualTo(AuditActorType.ANONYMOUS);
                    org.assertj.core.api.Assertions.assertThat(record.reasonCode()).isEqualTo("INVALID_CREDENTIALS");
                    org.assertj.core.api.Assertions.assertThat(serialized)
                            .doesNotContain("absent@example.test", password, "password");
                });
    }

    @Test void auditHistoryHasNoHttpMutationSurface() throws Exception {
        var admin = authentication.platformAdminJwt();
        mockMvc.perform(post("/api/audit-records").with(admin)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/audit-records").with(authentication.platformAdminJwt()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/api/audit-records").with(authentication.platformAdminJwt()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/audit-records").with(authentication.platformAdminJwt()))
                .andExpect(status().isMethodNotAllowed());
    }

    private void append(String businessId, String targetId) {
        AuditRequestContext.open("seed-" + targetId);
        try {
            audit.appendIsolated(AuditCommand.actionForBusiness(AuditAction.BRANCH_UPDATED,
                    AuditActor.system(), businessId, "BRANCH", targetId, AuditSource.SYSTEM),
                    AuditOutcome.SUCCESS, null);
        } finally {
            AuditRequestContext.close();
        }
    }

    private RegisterBusinessCommand business(String id) {
        return new RegisterBusinessCommand(id, id + " name", id + "@example.test",
                "0123456789", "reg-" + id);
    }

    private List<AuditRecord> platformRecords() {
        return records.queryPlatform(new AuditQuery(null, null, null, null, null,
                Instant.EPOCH, Instant.now().plusSeconds(60), null, 100));
    }
}
