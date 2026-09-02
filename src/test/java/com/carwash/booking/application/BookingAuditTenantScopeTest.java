package com.carwash.booking.application;

import com.carwash.access.application.TenantAccessContext;
import com.carwash.audit.application.AuditMetadataPolicy;
import com.carwash.audit.application.AuditOperations;
import com.carwash.audit.application.AuditPage;
import com.carwash.audit.application.AuditPrincipal;
import com.carwash.audit.application.AuditProperties;
import com.carwash.audit.application.AuditQueryRequest;
import com.carwash.audit.application.AuditQueryService;
import com.carwash.audit.application.AuditService;
import com.carwash.audit.domain.AuditAction;
import com.carwash.audit.domain.AuditOutcome;
import com.carwash.audit.domain.AuditQuery;
import com.carwash.audit.domain.AuditRecord;
import com.carwash.audit.domain.AuditRepository;
import com.carwash.audit.infrastructure.InMemoryAuditRepository;
import com.carwash.booking.domain.Booking;
import com.carwash.catalog.application.CreateServiceOfferingCommand;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.RoleName;
import com.carwash.identity.domain.User;
import com.carwash.marketplace.application.CreateBranchCommand;
import com.carwash.marketplace.application.RegisterBusinessCommand;
import com.carwash.shared.application.MutationLock;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.testsupport.ServiceTestSupport;
import com.carwash.testsupport.TestAccess;
import com.carwash.testsupport.TestDates;
import com.carwash.vehicle.domain.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingAuditTenantScopeTest extends ServiceTestSupport {

    private final AtomicInteger auditSequence = new AtomicInteger();
    private AuditProperties auditProperties;
    private InMemoryAuditRepository auditRecords;

    @BeforeEach
    void wireBookingAudit() {
        auditProperties = new AuditProperties(
                Duration.ofDays(365), 50, 200, Duration.ofDays(90), 10, 32, 256, 2048);
        AuditMetadataPolicy metadata = new AuditMetadataPolicy(auditProperties);
        auditRecords = new InMemoryAuditRepository(coordinator, metadata);
        bookingService = bookingServiceWith(new AuditService(
                auditRecords, coordinator, metadata,
                () -> "booking-audit-" + auditSequence.incrementAndGet(), clock));
    }

    @Test
    void customerCreationUsesCanonicalBusinessAndOwnerCanRetrieveIt() {
        CustomerFixture fixture = fixture("tenant-a");
        TenantAccessContext customer = TestAccess.customer(fixture.user().getUserId());

        Booking booking = create(customer, fixture, TestDates.future());

        AuditPage ownerA = ownerQuery(fixture.businessId(), AuditAction.BOOKING_CREATED, booking.getBookingId());
        assertThat(ownerA.records()).singleElement().satisfies(record -> {
            assertThat(record.businessId()).isEqualTo(fixture.businessId());
            assertThat(record.actorUserId()).isEqualTo(fixture.user().getUserId());
            assertThat(record.actorRole()).isEqualTo("CUSTOMER");
            assertThat(record.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        });
        assertThat(ownerQuery("another-business", AuditAction.BOOKING_CREATED, booking.getBookingId()).records())
                .isEmpty();
    }

    @Test
    void customerUpdateRescheduleCancelAndDeleteUseCanonicalBookingBusiness() {
        CustomerFixture fixture = fixture("lifecycle");
        TenantAccessContext customer = TestAccess.customer(fixture.user().getUserId());
        Booking booking = create(customer, fixture, TestDates.futureDays(10));

        bookingService.updateBooking(customer, booking.getBookingId(), fixture.vehicle().getVehicleId(),
                fixture.offeringId(), "updated but never audited");
        bookingService.rescheduleBooking(customer, booking.getBookingId(), TestDates.futureDays(11));
        bookingService.cancelBooking(customer, booking.getBookingId());
        bookingService.deleteBooking(customer, booking.getBookingId());

        List<AuditRecord> records = tenantRecords(fixture.businessId(), booking.getBookingId());
        assertThat(records).extracting(AuditRecord::action).containsExactlyInAnyOrder(
                AuditAction.BOOKING_CREATED,
                AuditAction.BOOKING_UPDATED,
                AuditAction.BOOKING_RESCHEDULED,
                AuditAction.BOOKING_CANCELLED,
                AuditAction.BOOKING_DELETED);
        assertThat(records).allSatisfy(record -> {
            assertThat(record.businessId()).isEqualTo(fixture.businessId());
            assertThat(record.actorRole()).isEqualTo("CUSTOMER");
            assertThat(record.outcome()).isEqualTo(AuditOutcome.SUCCESS);
            assertThat(record.metadata().toString()).doesNotContain("updated but never audited");
        });
    }

    @Test
    void oneCustomerAtTwoBusinessesProducesSeparatedTenantHistories() {
        User customerUser = registerUser();
        Vehicle vehicle = createVehicle(customerUser);
        CustomerFixture first = fixture("first", customerUser, vehicle);
        CustomerFixture second = fixture("second", customerUser, vehicle);
        TenantAccessContext customer = TestAccess.customer(customerUser.getUserId());

        Booking firstBooking = create(customer, first, TestDates.futureDays(20));
        Booking secondBooking = create(customer, second, TestDates.futureDays(21));

        assertThat(tenantRecords(first.businessId(), null))
                .filteredOn(record -> record.action() == AuditAction.BOOKING_CREATED)
                .extracting(AuditRecord::targetId).containsExactly(firstBooking.getBookingId());
        assertThat(tenantRecords(second.businessId(), null))
                .filteredOn(record -> record.action() == AuditAction.BOOKING_CREATED)
                .extracting(AuditRecord::targetId).containsExactly(secondBooking.getBookingId());
    }

    @Test
    void platformAdministratorMutationUsesCanonicalTargetBusiness() {
        CustomerFixture fixture = fixture("administrator");
        Booking booking = create(
                TestAccess.customer(fixture.user().getUserId()), fixture, TestDates.futureDays(30));

        bookingService.updateBooking(TestAccess.platformAdministrator(), booking.getBookingId(),
                fixture.vehicle().getVehicleId(), fixture.offeringId(), "administrator update");

        assertThat(records(AuditAction.BOOKING_UPDATED, booking.getBookingId()))
                .singleElement().satisfies(record -> {
                    assertThat(record.businessId()).isEqualTo(fixture.businessId());
                    assertThat(record.actorRole()).isEqualTo("PLATFORM_ADMIN");
                    assertThat(record.actorUserId()).isEqualTo("test-platform-administrator");
                    assertThat(record.outcome()).isEqualTo(AuditOutcome.SUCCESS);
                });
    }

    @Test
    void foreignAndMissingCustomerAttemptsRecordOneActorSafeDenialWithoutVictimTenant() {
        CustomerFixture victim = fixture("victim");
        Booking booking = create(
                TestAccess.customer(victim.user().getUserId()), victim, TestDates.futureDays(40));
        User attacker = registerUser();
        TenantAccessContext attackerAccess = TestAccess.customer(attacker.getUserId());

        ResourceNotFoundException foreign = org.junit.jupiter.api.Assertions.assertThrows(
                ResourceNotFoundException.class,
                () -> bookingService.updateBooking(attackerAccess, booking.getBookingId(),
                        victim.vehicle().getVehicleId(), victim.offeringId(), "forbidden"));
        ResourceNotFoundException missing = org.junit.jupiter.api.Assertions.assertThrows(
                ResourceNotFoundException.class,
                () -> bookingService.updateBooking(attackerAccess, "missing-booking",
                        victim.vehicle().getVehicleId(), victim.offeringId(), "missing"));

        assertThat(foreign.getMessage()).isEqualTo(missing.getMessage()).isEqualTo("Booking not found");
        assertActorSafeDenial(booking.getBookingId(), attacker.getUserId());
        assertActorSafeDenial("missing-booking", attacker.getUserId());
        assertThat(auditRecords.queryByBusinessId(victim.businessId(), query(
                AuditAction.BOOKING_UPDATED, booking.getBookingId()))).isEmpty();
    }

    @Test
    void failedMutationRollsBackProvisionalSuccessAndAppendsOneSafeFailure() {
        CustomerFixture fixture = fixture("failure");
        TenantAccessContext customer = TestAccess.customer(fixture.user().getUserId());
        Booking booking = create(customer, fixture, TestDates.futureDays(50));
        User other = registerUser();
        Vehicle otherVehicle = createVehicle(other);

        assertThatThrownBy(() -> bookingService.updateBooking(customer, booking.getBookingId(),
                otherVehicle.getVehicleId(), fixture.offeringId(), "must roll back"))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(records(AuditAction.BOOKING_UPDATED, booking.getBookingId()))
                .singleElement().satisfies(record -> {
                    assertThat(record.outcome()).isEqualTo(AuditOutcome.FAILURE);
                    assertThat(record.reasonCode()).isEqualTo("OPERATION_FAILED");
                    assertThat(record.businessId()).isNull();
                    assertThat(record.actorUserId()).isEqualTo(fixture.user().getUserId());
                });
        assertThat(bookingService.findById(booking.getBookingId()).getVehicle().getVehicleId())
                .isEqualTo(fixture.vehicle().getVehicleId());
    }

    @Test
    void mandatoryAuditInsertFailurePreventsBookingMutation() {
        CustomerFixture fixture = fixture("audit-insert-failure");
        TenantAccessContext customer = TestAccess.customer(fixture.user().getUserId());
        Booking booking = create(customer, fixture, TestDates.futureDays(60));
        BookingManagementService unavailableAuditService = bookingServiceWith(new AuditService(
                failingAuditRepository(), coordinator, new AuditMetadataPolicy(auditProperties),
                () -> "rejected-booking-audit", clock));

        assertThatThrownBy(() -> unavailableAuditService.updateBooking(
                customer, booking.getBookingId(), fixture.vehicle().getVehicleId(),
                fixture.offeringId(), "must never be applied"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected audit failure");

        assertThat(bookingService.findById(booking.getBookingId()).getSpecialRequest())
                .isEqualTo("initial request");
    }

    private BookingManagementService bookingServiceWith(AuditOperations audit) {
        return new BookingManagementService(
                bookingRepository, userRepository, vehicleRepository, catalogService,
                serviceOfferingService, marketplaceService, queueRepository, notificationRepository,
                notificationService, queueOrdering, coordinator, MutationLock.noOp(), bookingPolicy,
                bookingSlotPolicy,
                new BranchAvailabilityDecisionService(
                        bookingRepository, marketplaceService, branchSchedulingService,
                        serviceOfferingService, catalogService, bookingPolicy, clock),
                clock, audit);
    }

    private CustomerFixture fixture(String label) {
        User user = registerUser();
        return fixture(label, user, createVehicle(user));
    }

    private CustomerFixture fixture(String label, User user, Vehicle vehicle) {
        String businessId = ids.business();
        marketplaceService.registerBusiness(TestAccess.platformAdministrator(), new RegisterBusinessCommand(
                businessId, label + " Car Wash", ids.emailFor(businessId), "+27821234567", null));
        String branchId = ids.branch();
        marketplaceService.createBranch(TestAccess.platformAdministrator(), businessId, new CreateBranchCommand(
                branchId, label + " Branch", "1 Test Street", null, "Cape Town", "Western Cape",
                "8001", "ZA", BigDecimal.ZERO, BigDecimal.ZERO, "UTC", true));
        branchSchedulingService.replaceOperatingSchedule(
                TestAccess.platformAdministrator(), branchId,
                new com.carwash.marketplace.application.ReplaceOperatingScheduleCommand(
                        Arrays.stream(DayOfWeek.values())
                                .map(day -> new com.carwash.marketplace.application.WeeklyOperatingIntervalCommand(
                                        day, LocalTime.of(8, 0), LocalTime.of(17, 0)))
                                .toList()));
        Service service = createService();
        String offeringId = ids.offering();
        serviceOfferingService.createOffering(
                TestAccess.platformAdministrator(), branchId,
                new CreateServiceOfferingCommand(
                        offeringId, service.getServiceId(), service.getPrice(),
                        service.getEstimatedDurationMin(), bookingPolicy.maxActiveBookingsPerSlot()));
        return new CustomerFixture(user, vehicle, businessId, branchId, offeringId);
    }

    private Booking create(
            TenantAccessContext customer,
            CustomerFixture fixture,
            LocalDateTime scheduledAt
    ) {
        return bookingService.createBooking(
                customer, ids.booking(), fixture.user().getUserId(), fixture.vehicle().getVehicleId(),
                fixture.branchId(), fixture.offeringId(), scheduledAt, "initial request");
    }

    private AuditPage ownerQuery(String businessId, AuditAction action, String targetId) {
        AuditQueryService queryService = new AuditQueryService(
                auditRecords, coordinator, AuditOperations.noOp(), auditProperties, clock);
        return queryService.query(
                new AuditPrincipal("owner-" + businessId, RoleName.BUSINESS_OWNER.name(), businessId),
                new AuditQueryRequest(
                        null, action, null, "BOOKING", targetId,
                        null, null, null, 100, null, null));
    }

    private List<AuditRecord> tenantRecords(String businessId, String bookingId) {
        return auditRecords.queryByBusinessId(businessId, query(null, bookingId));
    }

    private List<AuditRecord> records(AuditAction action, String bookingId) {
        return auditRecords.queryPlatform(query(action, bookingId));
    }

    private AuditQuery query(AuditAction action, String bookingId) {
        return new AuditQuery(
                null, action, null, "BOOKING", bookingId,
                Instant.parse("2088-01-01T00:00:00Z"),
                Instant.parse("2090-12-31T23:59:59Z"), null, 100);
    }

    private void assertActorSafeDenial(String bookingId, String actorUserId) {
        assertThat(records(AuditAction.BOOKING_UPDATED, bookingId)).singleElement().satisfies(record -> {
            assertThat(record.outcome()).isEqualTo(AuditOutcome.DENIED);
            assertThat(record.reasonCode()).isEqualTo("RESOURCE_NOT_FOUND_OR_FOREIGN");
            assertThat(record.businessId()).isNull();
            assertThat(record.actorUserId()).isEqualTo(actorUserId);
            assertThat(record.actorRole()).isEqualTo("CUSTOMER");
        });
    }

    private static AuditRepository failingAuditRepository() {
        return new AuditRepository() {
            @Override
            public void append(AuditRecord record) {
                throw new IllegalStateException("injected audit failure");
            }

            @Override
            public List<AuditRecord> queryByBusinessId(String businessId, AuditQuery query) {
                return List.of();
            }

            @Override
            public List<AuditRecord> queryPlatform(AuditQuery query) {
                return List.of();
            }
        };
    }

    private record CustomerFixture(
            User user,
            Vehicle vehicle,
            String businessId,
            String branchId,
            String offeringId
    ) { }
}
