package com.carwash.feedback.application;

import com.carwash.access.application.TenantAccessContext;
import com.carwash.audit.application.AuditOperations;
import com.carwash.audit.application.AuditCommand;
import com.carwash.audit.domain.AuditOutcome;
import com.carwash.booking.application.*;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.catalog.application.*;
import com.carwash.feedback.domain.*;
import com.carwash.feedback.infrastructure.InMemoryFeedbackRepository;
import com.carwash.identity.domain.RoleName;
import com.carwash.marketplace.application.*;
import com.carwash.shared.application.*;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import com.carwash.shared.exception.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.security.access.AccessDeniedException;

class FeedbackServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);
    private final InMemoryDataCoordinator tx = new InMemoryDataCoordinator();
    private final InMemoryFeedbackRepository repository = new InMemoryFeedbackRepository(tx);
    private final Map<String, BookingReviewEligibility> bookingFixtures = new ConcurrentHashMap<>();
    private final Map<String, BranchSnapshot> branchFixtures = new ConcurrentHashMap<>();
    private final Map<String, BusinessSnapshot> businessFixtures = new ConcurrentHashMap<>();
    private final Map<String, ServiceOfferingSnapshot> offeringFixtures = new ConcurrentHashMap<>();
    private final BookingQuery bookings = stub(BookingQuery.class, (method,args) -> {
        if (method.equals("findReviewEligibility")) return Optional.ofNullable(bookingFixtures.get(args[0]))
                .filter(b -> b.customerId().equals(args[1]));
        throw new IllegalStateException(method);
    });
    private final MarketplaceQuery marketplace = stub(MarketplaceQuery.class, (method,args) -> switch(method) {
        case "findBranchOptional" -> Optional.ofNullable(branchFixtures.get(args[0]));
        case "findBusinessOptional" -> Optional.ofNullable(businessFixtures.get(args[0]));
        default -> throw new IllegalStateException(method);
    });
    private final ServiceOfferingQuery offerings = stub(ServiceOfferingQuery.class, (method,args) -> {
        if (method.equals("findOfferingOptional")) return Optional.ofNullable(offeringFixtures.get(args[0]));
        throw new IllegalStateException(method);
    });
    private interface Call { Object invoke(String method, Object[] args); }
    @SuppressWarnings("unchecked") private static <T> T stub(Class<T> contract, Call call) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},
                (proxy, method, args) -> call.invoke(method.getName(),args));
    }
    private final TenantAccessContext customer = new TenantAccessContext("customer", RoleName.CUSTOMER, null);
    private final TenantAccessContext foreign = new TenantAccessContext("foreign", RoleName.CUSTOMER, null);
    private final TenantAccessContext owner = new TenantAccessContext("owner", RoleName.BUSINESS_OWNER, "business");
    private final TenantAccessContext otherOwner = new TenantAccessContext("other", RoleName.BUSINESS_OWNER, "other");
    private final TenantAccessContext admin = new TenantAccessContext("admin", RoleName.PLATFORM_ADMIN, null);
    private final AuditOperations audit = new AuditOperations() {
        @Override public <T> T execute(AuditCommand command, Supplier<T> mutation) { return tx.write(mutation); }
        @Override public <T> T execute(AuditCommand success, AuditCommand failure, Supplier<T> mutation) {
            return tx.write(mutation);
        }
        @Override public <T> T executeDeferred(Supplier<AuditCommand> success, AuditCommand failure,
                Supplier<T> mutation) { return tx.write(() -> { success.get(); return mutation.get(); }); }
        @Override public void appendIsolated(AuditCommand command, AuditOutcome outcome, String code) { }
    };
    private final FeedbackService service = new FeedbackService(repository, bookings, marketplace, offerings,
            tx, MutationLock.noOp(), audit, Telemetry.NONE, clock);

    @BeforeEach void fixtures() {
        branchFixtures.put("branch",branch(true));
        businessFixtures.put("business",new BusinessSnapshot("business",
                "Business", null, null, null, null, null, null));
        offeringFixtures.put("offering",new ServiceOfferingSnapshot(
                "offering", "branch", "service", "Wash", null, BigDecimal.ONE, 30, 1,
                null, true, true, null, null));
    }
    private BranchSnapshot branch(boolean discoverable) {
        return new BranchSnapshot("branch", "business", "Branch", null,null,null,null,null,null,
                null,null,"Africa/Johannesburg",null,true,true,discoverable,null,null);
    }
    private void booking(String id, BookingStatus status, LocalDateTime at) {
        bookingFixtures.put(id,new BookingReviewEligibility(
                id,"customer","branch","offering","service",at,status));
    }
    private void completed(String id) { booking(id,BookingStatus.COMPLETED,LocalDateTime.of(2026,10,11,12,0)); }

    @Test void completionOwnershipValidationAndDuplicateConflict() {
        for (BookingStatus status : BookingStatus.values()) {
            if (status == BookingStatus.COMPLETED) continue;
            String id = status.name(); booking(id,status,LocalDateTime.of(2026,10,11,12,0));
            assertThrows(BusinessRuleViolationException.class, () -> service.submit(customer,id,5,null));
        }
        booking("future",BookingStatus.COMPLETED,LocalDateTime.of(2027,1,1,12,0));
        assertThrows(BusinessRuleViolationException.class, () -> service.submit(customer,"future",5,null));
        completed("booking");
        assertThrows(ResourceNotFoundException.class, () -> service.submit(foreign,"booking",5,null));
        assertThrows(AccessDeniedException.class, () -> service.submit(owner,"booking",5,null));
        assertThrows(BusinessRuleViolationException.class, () -> service.submit(customer,"booking",0,null));
        assertThrows(BusinessRuleViolationException.class, () -> service.submit(customer,"booking",5,"x".repeat(1001)));
        Review review = service.submit(customer,"booking",5,"plain <b>text</b>");
        assertEquals("plain <b>text</b>",review.comment());
        assertEquals(Review.class, review.getClass());
        assertThrows(ResourceConflictException.class, () -> service.submit(customer,"booking",4,null));
        assertEquals(review,service.ownReview(customer,"booking"));
        assertThrows(ResourceNotFoundException.class, () -> service.ownReview(foreign,"booking"));
    }

    @Test void summariesReportsModerationAndTenantPages() {
        completed("one"); completed("two"); completed("three");
        Review first = service.submit(customer,"one",1,"one");
        service.submit(customer,"two",2,null);
        service.submit(customer,"three",5,null);
        RatingSummary summary = service.branchSummary("branch");
        assertEquals(3,summary.reviewCount());
        assertEquals(new BigDecimal("2.67"),summary.averageRating());
        assertEquals(List.of(1L,1L,0L,0L,1L),summary.distribution());
        assertEquals(summary,service.offeringSummary("offering"));
        var page = service.tenantReviews(owner," business ",FeedbackListPolicy.REVIEWS.parse(Map.of(
                "rating",List.of("5"),"page",List.of("2"),"size",List.of("1"))));
        assertEquals(1,page.totalElements()); assertTrue(page.content().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.tenantReviews(otherOwner,"business",
                FeedbackListPolicy.REVIEWS.parse(Map.of())));
        assertThrows(ResourceNotFoundException.class, () -> service.tenantReviews(admin,"missing",
                FeedbackListPolicy.REVIEWS.parse(Map.of())));
        ReviewReport report = service.report(owner,first.id(),"Concern");
        assertEquals(summary,service.branchSummary("branch"));
        assertThrows(ResourceConflictException.class, () -> service.report(owner,first.id(),"Again"));
        assertThrows(ResourceNotFoundException.class, () -> service.report(otherOwner,first.id(),"Other tenant"));
        assertThrows(AccessDeniedException.class, () -> service.moderate(owner,first.id(),PublicationState.HIDDEN,"Hide"));
        service.moderate(admin,first.id(),PublicationState.HIDDEN,"Reviewed");
        assertEquals(2,service.branchSummary("branch").reviewCount());
        assertEquals("one",service.ownReview(customer,"one").comment());
        assertEquals(1,service.moderationQueue(admin,"business",FeedbackListPolicy.REPORTS.parse(Map.of(
                "state",List.of("OPEN")))).totalElements());
        service.resolve(admin,report.id(),"Handled");
        assertEquals(0,service.moderationQueue(admin,"business",FeedbackListPolicy.REPORTS.parse(Map.of(
                "state",List.of("OPEN")))).totalElements());
        service.moderate(admin,first.id(),PublicationState.PUBLISHED,"Restored");
        assertEquals(summary,service.branchSummary("branch"));
        branchFixtures.put("branch",branch(false));
        assertThrows(ResourceNotFoundException.class, () -> service.branchSummary("branch"));
        assertThrows(ResourceNotFoundException.class, () -> service.offeringSummary("offering"));
        assertEquals(3,service.tenantReviews(owner,"business",FeedbackListPolicy.REVIEWS.parse(Map.of())).totalElements());
    }

    @Test void concurrentSubmissionHasExactlyOneWinner() throws Exception {
        completed("concurrent");
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) results.add(pool.submit(() -> {
                start.await();
                try { service.submit(customer,"concurrent",5,null); return true; }
                catch (ResourceConflictException duplicate) { return false; }
            }));
            assertEquals(1,results.stream().filter(f -> {
                try { return f.get(); } catch (Exception failure) { throw new AssertionError(failure); }
            }).count());
        }
        assertEquals(1,service.branchSummary("branch").reviewCount());
    }

    @Test void mandatoryAuditFailureAndOuterRollbackLeaveNoReview() {
        completed("audit-failure");
        AuditOperations failing = new AuditOperations() {
            @Override public <T> T execute(AuditCommand command, Supplier<T> mutation) {
                return tx.write(() -> { T value = mutation.get(); throw new IllegalStateException("audit failed"); });
            }
            @Override public <T> T execute(AuditCommand success, AuditCommand failure, Supplier<T> mutation) {
                return execute(success,mutation);
            }
            @Override public <T> T executeDeferred(Supplier<AuditCommand> success, AuditCommand failure,
                    Supplier<T> mutation) {
                return tx.write(() -> { success.get(); mutation.get(); throw new IllegalStateException("audit failed"); });
            }
            @Override public void appendIsolated(AuditCommand command, AuditOutcome outcome, String code) { }
        };
        var protectedService = new FeedbackService(repository,bookings,marketplace,offerings,tx,
                MutationLock.noOp(),failing,Telemetry.NONE,clock);
        var error = assertThrows(IllegalStateException.class,
                () -> protectedService.submit(customer,"audit-failure",4,"original"));
        assertEquals("audit failed",error.getMessage());
        assertTrue(repository.byBooking("audit-failure").isEmpty());
        assertEquals(0,service.branchSummary("branch").reviewCount());
    }
}
