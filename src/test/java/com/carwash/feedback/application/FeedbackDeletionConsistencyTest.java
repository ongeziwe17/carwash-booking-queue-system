package com.carwash.feedback.application;

import com.carwash.access.application.TenantAccessContext;
import com.carwash.audit.application.*;
import com.carwash.audit.infrastructure.InMemoryAuditRepository;
import com.carwash.feedback.domain.*;
import com.carwash.feedback.infrastructure.InMemoryFeedbackRepository;
import com.carwash.identity.application.UserManagementService;
import com.carwash.identity.domain.*;
import com.carwash.identity.infrastructure.*;
import com.carwash.notification.domain.Notification;
import com.carwash.notification.infrastructure.InMemoryNotificationRepository;
import com.carwash.shared.application.*;
import com.carwash.shared.exception.*;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackDeletionConsistencyTest {
    private final InMemoryDataCoordinator tx = new InMemoryDataCoordinator();
    private final InMemoryFeedbackRepository feedback = new InMemoryFeedbackRepository(tx);
    private final InMemoryUserRepository accounts = new InMemoryUserRepository();
    private final InMemoryTenantMembershipRepository memberships = new InMemoryTenantMembershipRepository();
    private final InMemoryNotificationRepository notifications = new InMemoryNotificationRepository(null);
    private final UserManagementService users = new UserManagementService(accounts,
            org.mockito.Mockito.mock(com.carwash.identity.application.CredentialService.class),null,null,
            notifications,memberships,tx,MutationLock.noOp(),feedback::referencesUser);
    private final AuditMetadataPolicy auditPolicy = new AuditMetadataPolicy(new AuditProperties(
            Duration.ofDays(30),20,100,Duration.ofDays(30),8,64,256,2048));
    private final AuditService audit = new AuditService(new InMemoryAuditRepository(tx,auditPolicy),tx,
            auditPolicy,() -> UUID.randomUUID().toString(),Clock.systemUTC());

    private User account(String id, RoleName role) {
        User value = User.withEncodedPassword(id,id,id+"@example.test","0800000000","encoded",
                RoleCatalog.role(role));
        value.registerAccount(); assertTrue(accounts.insert(value)); return value;
    }
    private Review review(String moderator) {
        Review value = new Review("review","booking","customer","tenant","branch","offering","service",5,
                "Original comment",Instant.parse("2026-10-13T00:00:00Z"),PublicationState.HIDDEN,
                "Private reason",moderator,Instant.parse("2026-10-13T01:00:00Z"));
        tx.write(() -> assertTrue(feedback.insert(value)));
        return value;
    }

    @Test void everyHistoricalActorReferenceBlocksDeletionBeforeCleanup() {
        User owner = account("owner",RoleName.BUSINESS_OWNER);
        account("moderator",RoleName.PLATFORM_ADMIN);
        account("resolver",RoleName.PLATFORM_ADMIN);
        account("spare-admin",RoleName.PLATFORM_ADMIN);
        account("free",RoleName.CUSTOMER);
        assertTrue(memberships.insert(new TenantMembership("owner","tenant",LocalDateTime.now())));
        assertTrue(notifications.insert(new Notification("owner-notice",owner,null,"SYSTEM","Retained","IN_APP")));
        review("moderator");
        tx.write(() -> assertTrue(feedback.insertReport(new ReviewReport("report","review","tenant","owner",
                "Original report",Instant.parse("2026-10-13T02:00:00Z"),ReportState.RESOLVED,
                "Resolution", "resolver",Instant.parse("2026-10-13T03:00:00Z")))));
        for (String id : List.of("owner","moderator","resolver")) {
            var error = assertThrows(BusinessRuleViolationException.class,() -> users.deleteUser(id));
            assertEquals("User cannot be deleted while feedback references it",error.getMessage());
            assertTrue(accounts.findById(id).isPresent());
        }
        assertEquals("tenant",memberships.findById("owner").orElseThrow().businessId());
        assertEquals(1,notifications.findByUserId("owner").size());
        assertEquals("Original comment",feedback.byId("review").orElseThrow().comment());
        assertEquals("Original report",feedback.reportById("report").orElseThrow().reason());
        users.deleteUser("free");
        assertTrue(accounts.findById("free").isEmpty());
    }

    @Test void concurrentReportingAndDeletionCannotLeaveAnOrphanActor() throws Exception {
        account("owner",RoleName.BUSINESS_OWNER);
        account("spare-admin",RoleName.PLATFORM_ADMIN);
        review("spare-admin");
        FeedbackService service = new FeedbackService(feedback,null,null,null,tx,MutationLock.noOp(),
                audit,Telemetry.NONE,Clock.systemUTC(),users);
        race("owner",() -> service.report(new TenantAccessContext("owner",RoleName.BUSINESS_OWNER,"tenant"),
                "review","Concern"));
    }

    @Test void concurrentModerationAndResolutionAlsoSerializeWithDeletion() throws Exception {
        account("moderator",RoleName.PLATFORM_ADMIN);
        account("resolver",RoleName.PLATFORM_ADMIN);
        account("spare-admin",RoleName.PLATFORM_ADMIN);
        review("spare-admin");
        tx.write(() -> assertTrue(feedback.insertReport(new ReviewReport("report","review","tenant",
                "spare-admin","Concern",Instant.now(),ReportState.OPEN,null,null,null))));
        FeedbackService service = new FeedbackService(feedback,null,null,null,tx,MutationLock.noOp(),
                audit,Telemetry.NONE,Clock.systemUTC(),users);
        race("moderator",() -> service.moderate(new TenantAccessContext("moderator",RoleName.PLATFORM_ADMIN,null),
                "review",PublicationState.PUBLISHED,"Reviewed"));
        race("resolver",() -> service.resolve(new TenantAccessContext("resolver",RoleName.PLATFORM_ADMIN,null),
                "report","Resolved"));
    }

    private void race(String actorId, Runnable reference) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> mutation = pool.submit(() -> {
                start.await();
                try { reference.run(); }
                catch (ResourceNotFoundException expectedIfDeletedFirst) { }
                return null;
            });
            Future<?> deletion = pool.submit(() -> {
                start.await();
                try { users.deleteUser(actorId); }
                catch (BusinessRuleViolationException expectedIfReportedFirst) { }
                return null;
            });
            mutation.get(10,TimeUnit.SECONDS); deletion.get(10,TimeUnit.SECONDS);
        }
        assertEquals(accounts.findById(actorId).isPresent(),feedback.referencesUser(actorId));
    }
}
