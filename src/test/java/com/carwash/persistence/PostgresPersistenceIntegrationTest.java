package com.carwash.persistence;

import com.carwash.CarwashBookingQueueSystemApplication;
import com.carwash.access.application.UserAuthenticationService;
import com.carwash.access.application.TenantAccessContext;
import com.carwash.booking.application.BookingManagementService;
import com.carwash.booking.application.BranchAvailabilitySearchCriteria;
import com.carwash.booking.application.BranchAvailabilitySearchService;
import com.carwash.booking.domain.Booking;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.catalog.application.CreateServiceOfferingCommand;
import com.carwash.catalog.application.ServiceCatalogService;
import com.carwash.catalog.application.ServiceOfferingService;
import com.carwash.catalog.application.ServiceOfferingCapacityQuery;
import com.carwash.catalog.application.UpdateServiceOfferingCommand;
import com.carwash.catalog.domain.Service;
import com.carwash.catalog.domain.ServiceOffering;
import com.carwash.catalog.domain.ServiceOfferingRepository;
import com.carwash.catalog.domain.ServiceOfferingStatus;
import com.carwash.catalog.domain.ServiceRepository;
import com.carwash.identity.application.CreateUserCommand;
import com.carwash.identity.application.UserManagementService;
import com.carwash.identity.application.TenantMembershipManagementService;
import com.carwash.identity.domain.User;
import com.carwash.identity.domain.UserRepository;
import com.carwash.identity.domain.RoleRepository;
import com.carwash.identity.domain.RoleName;
import com.carwash.identity.domain.TenantMembershipRepository;
import com.carwash.marketplace.application.BranchSchedulingService;
import com.carwash.marketplace.application.CreateBranchCommand;
import com.carwash.marketplace.application.MarketplaceManagementService;
import com.carwash.marketplace.application.RegisterBusinessCommand;
import com.carwash.marketplace.application.ReplaceOperatingScheduleCommand;
import com.carwash.marketplace.application.UpdateBranchCommand;
import com.carwash.marketplace.application.WeeklyOperatingIntervalCommand;
import com.carwash.marketplace.domain.BranchOperatingScheduleRepository;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.marketplace.domain.CarWashBusinessRepository;
import com.carwash.marketplace.domain.TemporaryBranchClosureRepository;
import com.carwash.notification.application.NotificationManagementService;
import com.carwash.notification.domain.DeliveryStatus;
import com.carwash.notification.domain.Notification;
import com.carwash.notification.domain.NotificationCursor;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.queue.application.QueueManagementService;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.recommendation.application.RecommendationPreference;
import com.carwash.recommendation.application.RecommendationSearchCriteria;
import com.carwash.recommendation.application.RecommendationService;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.vehicle.application.VehicleManagementService;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.vehicle.domain.VehicleRepository;
import com.carwash.audit.application.AuditActor;
import com.carwash.audit.application.AuditCommand;
import com.carwash.audit.application.AuditOperations;
import com.carwash.audit.application.AuditRequestContext;
import com.carwash.audit.domain.AuditAction;
import com.carwash.audit.domain.AuditOutcome;
import com.carwash.audit.domain.AuditSource;
import com.carwash.audit.domain.AuditRepository;
import com.carwash.audit.domain.AuditQuery;
import com.carwash.audit.domain.AuditRecord;
import com.carwash.marketplace.application.UpdateBusinessCommand;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles(profiles={"test","postgres"},inheritProfiles=false)
@org.springframework.context.annotation.Import(PostgresPersistenceIntegrationTest.PageStatementConfiguration.class)
@Testcontainers
class PostgresPersistenceIntegrationTest {
    private static final String PASSWORD="StrongPassword!123";
    private static final TenantAccessContext ADMIN =
            new TenantAccessContext("test-platform-admin", RoleName.PLATFORM_ADMIN, null);
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.6-alpine3.22")
            .withUrlParam("socketTimeout", "2");

    @DynamicPropertySource static void postgres(DynamicPropertyRegistry properties){
        properties.add("spring.autoconfigure.exclude",()->"");
        properties.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username",POSTGRES::getUsername);
        properties.add("spring.datasource.password",POSTGRES::getPassword);
        properties.add("spring.datasource.hikari.connection-timeout", () -> "1000");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired org.springframework.test.web.servlet.MockMvc managementMvc;
    @Autowired com.carwash.queue.infrastructure.QueueTelemetrySampler telemetrySampler;
    @Autowired io.micrometer.core.instrument.MeterRegistry telemetryMeters;

    @Autowired PageStatements pageStatements;

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class PageStatementConfiguration {
        @org.springframework.context.annotation.Bean PageStatements pageStatements() { return new PageStatements(); }
        @org.springframework.context.annotation.Bean
        org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer pageStatementCustomizer(PageStatements statements) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", statements);
        }
    }
    static class PageStatements implements org.hibernate.resource.jdbc.spi.StatementInspector {
        private final java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<String>> hook =
                new java.util.concurrent.atomic.AtomicReference<>();
        @Override public String inspect(String sql) {
            var observer = hook.get(); if(observer != null) observer.accept(sql); return sql;
        }
    }

    @Test void contentAndCountStayOnOneSnapshotAcrossAControlledConcurrentCommit() throws Exception {
        var initial=service("page-snapshot-initial"); initial.setActive(false); initial.setServiceName("Snapshot cohort"); assertTrue(serviceRepository.insert(initial));
        var query=com.carwash.catalog.application.ServiceListPolicy.POLICY.parse(java.util.Map.of("active",java.util.List.of("false"),"name",java.util.List.of("Snapshot cohort")));
        long before=transactions.readSnapshot(() -> serviceRepository.findPage(query).totalElements());
        var contentReached=new CountDownLatch(1); var committed=new CountDownLatch(1);
        pageStatements.hook.set(sql -> {
            if(sql.toLowerCase(java.util.Locale.ROOT).contains("from service_definitions") && sql.contains("fetch first")) {
                pageStatements.hook.set(null); contentReached.countDown();
                try { assertTrue(committed.await(10,TimeUnit.SECONDS)); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }
        });
        try(var executor=Executors.newFixedThreadPool(2)) {
            var reader=executor.submit(() -> transactions.readSnapshot(() -> serviceRepository.findPage(query)));
            assertTrue(contentReached.await(10,TimeUnit.SECONDS));
            var writer=executor.submit(() -> transactions.write(() -> {
                var later=service("page-snapshot-later"); later.setActive(false); later.setServiceName("Snapshot cohort"); assertTrue(serviceRepository.insert(later));
            }));
            writer.get(10,TimeUnit.SECONDS); committed.countDown();
            var page=reader.get(10,TimeUnit.SECONDS);
            assertEquals(before,page.totalElements());
            assertFalse(page.content().stream().anyMatch(v -> v.getServiceId().equals("page-snapshot-later")));
            assertEquals(before+1,transactions.readSnapshot(() -> serviceRepository.findPage(query).totalElements()));
        } finally { committed.countDown(); pageStatements.hook.set(null); }
    }

    @Test void persistentAndMemoryPagesAgreeOnFiltersSortTiesPrecisionAndEmptyMetadata() {
        var memory=new com.carwash.catalog.infrastructure.InMemoryServiceRepository();
        for(int i=0;i<23;i++) {
            var value=service("page-equivalence-"+String.format("%02d",i)); value.setActive(true);
            value.setPrice(new BigDecimal("123.45")); value.setServiceName("Page equivalence cohort"); assertTrue(serviceRepository.insert(value)); memory.insert(value);
        }
        for(String direction:java.util.List.of("asc","desc")) for(int page:java.util.List.of(0,1,2,3,4)) {
            var query=com.carwash.catalog.application.ServiceListPolicy.POLICY.parse(java.util.Map.of(
                    "name",java.util.List.of("Page equivalence cohort"),"active",java.util.List.of("true"),
                    "sort",java.util.List.of("price,"+direction,"id,"+direction),"size",java.util.List.of("7"),"page",java.util.List.of(String.valueOf(page))));
            var persistent=transactions.readSnapshot(() -> serviceRepository.findPage(query));var inMemory=memory.findPage(query);
            assertEquals(23,persistent.totalElements());assertEquals(inMemory.totalPages(),persistent.totalPages());
            assertEquals(inMemory.content().stream().map(Service::getServiceId).toList(),persistent.content().stream().map(Service::getServiceId).toList());
            assertEquals(inMemory.hasNext(),persistent.hasNext());assertEquals(inMemory.hasPrevious(),persistent.hasPrevious());
        }
        var f=fixture("page-precision",1);
        var at=LocalDateTime.of(2089,1,17,10,0,0,123456789);
        var booking=bookings.createBooking(ADMIN,"page-precision-booking",f.user.getUserId(),f.vehicle.getVehicleId(),f.branchId,f.offeringId,at.withNano(0),null);
        booking.setScheduledDateTime(at); transactions.write(() -> assertTrue(bookingRepository.update(booking)));
        var memBookings=new com.carwash.booking.infrastructure.InMemoryBookingRepository();memBookings.insert(booking);
        for(int page:java.util.List.of(0,1,100000)) {
            var query=com.carwash.booking.application.BookingListPolicy.POLICY.parse(java.util.Map.of(
                    "userId",java.util.List.of(f.user.getUserId()),"from",java.util.List.of(at.toString()),
                    "to",java.util.List.of(at.toString()),"page",java.util.List.of(String.valueOf(page)),"size",java.util.List.of("1")));
            var persistent=transactions.readSnapshot(() -> bookingRepository.findPage(query));var inMemory=memBookings.findPage(query);
            assertEquals(inMemory.totalElements(),persistent.totalElements());assertEquals(inMemory.totalPages(),persistent.totalPages());
            assertEquals(inMemory.content().stream().map(Booking::getBookingId).toList(),persistent.content().stream().map(Booking::getBookingId).toList());
            assertEquals(inMemory.hasPrevious(),persistent.hasPrevious());assertEquals(inMemory.hasNext(),persistent.hasNext());
        }
        var after=com.carwash.booking.application.BookingListPolicy.POLICY.parse(java.util.Map.of(
                "userId",java.util.List.of(f.user.getUserId()),"from",java.util.List.of(at.plusNanos(1).toString())));
        assertEquals(0,transactions.readSnapshot(() -> bookingRepository.findPage(after)).totalElements());
    }

    @Test void snapshotCannotSilentlyJoinAReadCommittedTransaction() {
        var query=com.carwash.catalog.application.ServiceListPolicy.POLICY.parse(java.util.Map.of());
        assertThrows(IllegalStateException.class, () -> transactions.read(() -> transactions.readSnapshot(() -> serviceRepository.findPage(query))));
    }

    @Test void persistentScopePredicatesAndEligibilityApplyBeforeTotalsAndLimits() {
        var f=fixture("page-query-owner",2);
        Booking booking=bookings.createBooking(ADMIN,"page-query-booking",f.user.getUserId(),f.vehicle.getVehicleId(),f.branchId,f.offeringId,
                LocalDateTime.of(2089,1,17,10,0),null);
        bookings.confirmBooking(ADMIN,booking.getBookingId());queues.createQueueEntry(ADMIN,"page-query-entry",booking.getBookingId(),f.service.getServiceId());
        var scope=new com.carwash.shared.application.ListScope("page-query-owner-business",f.branchId,null,false);
        transactions.readSnapshot(() -> {
            assertEquals(1,bookingRepository.findPage(com.carwash.booking.application.BookingListPolicy.POLICY.parse(java.util.Map.of()).scoped(scope)).totalElements());
            assertEquals(1,queueRepository.findPage(com.carwash.queue.application.QueueListPolicy.POLICY.parse(java.util.Map.of()).scoped(scope)).totalElements());
            assertEquals(1,vehicleRepository.findPage(com.carwash.vehicle.application.VehicleListPolicy.POLICY.parse(java.util.Map.of()).scoped(scope)).totalElements());
            assertEquals(1,branchRepository.findPage(com.carwash.marketplace.application.BranchListPolicy.POLICY.parse(java.util.Map.of()).scoped(scope)).totalElements());
            assertEquals(1,offeringRepository.findPage(com.carwash.catalog.application.OfferingListPolicy.POLICY.parse(java.util.Map.of()).scoped(scope)).totalElements());
            assertEquals(1,businessRepository.findPage(com.carwash.marketplace.application.BusinessListPolicy.POLICY.parse(java.util.Map.of()).scoped(scope)).totalElements());
            var foreign=new com.carwash.shared.application.ListScope("page-query-foreign",null,null,false);
            assertEquals(0,bookingRepository.findPage(com.carwash.booking.application.BookingListPolicy.POLICY.parse(java.util.Map.of("page",java.util.List.of("999999"))).scoped(foreign)).totalElements());
            return null;
        });
    }

    @Test void closureRangesUseExactInstantsAndDiscoveryTotalsTrackEligibility() {
        var f=fixture("page-eligibility",1);
        var start=Instant.parse("2089-01-18T10:00:00.123456789Z");
        scheduling.createTemporaryClosure(ADMIN,f.branchId,new com.carwash.marketplace.application.CreateTemporaryBranchClosureCommand(
                "page-closure",start,start.plusSeconds(10),"closure"));
        var scope=new com.carwash.shared.application.ListScope("page-eligibility-business",f.branchId,null,false);
        var exact=com.carwash.marketplace.application.ClosureListPolicy.POLICY.parse(java.util.Map.of(
                "from",java.util.List.of(start.toString()),"to",java.util.List.of(start.toString()),"status",java.util.List.of("ACTIVE"))).scoped(scope);
        var memBranches=new com.carwash.marketplace.infrastructure.InMemoryCarWashBranchRepository();
        memBranches.insert(branchRepository.findById(f.branchId).orElseThrow());
        var mem=new com.carwash.marketplace.infrastructure.InMemoryTemporaryBranchClosureRepository(memBranches);
        mem.insert(closureRepository.findById("page-closure").orElseThrow());
        assertEquals(mem.findPage(exact).totalElements(),transactions.readSnapshot(() -> closureRepository.findPage(exact).totalElements()));
        var after=com.carwash.marketplace.application.ClosureListPolicy.POLICY.parse(java.util.Map.of("from",java.util.List.of(start.plusNanos(1).toString()))).scoped(scope);
        assertEquals(0,transactions.readSnapshot(() -> closureRepository.findPage(after).totalElements()));
        var userQuery=com.carwash.identity.application.UserListPolicy.POLICY.parse(java.util.Map.of("id",java.util.List.of(f.user.getUserId()),"sort",java.util.List.of("createdAt,desc","name,asc")));
        var pageSql=new java.util.concurrent.CopyOnWriteArrayList<String>(); pageStatements.hook.set(pageSql::add);
        try {
            var userPage=transactions.readSnapshot(() -> userRepository.findPage(userQuery));
            assertEquals(1,userPage.totalElements()); assertNull(userPage.content().getFirst().getEncodedPassword());
            assertEquals(f.user.getFullName(),userPage.content().getFirst().getFullName());
            assertFalse(pageSql.stream().anyMatch(sql -> sql.contains("user_credentials")));
        } finally { pageStatements.hook.set(null); }
        var discoveryScope=new com.carwash.shared.application.ListScope("page-eligibility-business",f.branchId,null,true);
        var branchQuery=com.carwash.marketplace.application.DiscoverableBranchListPolicy.POLICY.parse(java.util.Map.of()).scoped(discoveryScope);
        var offeringQuery=com.carwash.catalog.application.DiscoverableOfferingListPolicy.POLICY.parse(java.util.Map.of()).scoped(discoveryScope);
        assertEquals(1,transactions.readSnapshot(() -> branchRepository.findPage(branchQuery).totalElements()));
        assertEquals(1,transactions.readSnapshot(() -> offeringRepository.findPage(offeringQuery).totalElements()));
        catalog.deactivateService(ADMIN,f.service.getServiceId());
        assertEquals(0,transactions.readSnapshot(() -> offeringRepository.findPage(offeringQuery).totalElements()));
        marketplace.deactivateBusiness(ADMIN,"page-eligibility-business");
        assertEquals(0,transactions.readSnapshot(() -> branchRepository.findPage(branchQuery).totalElements()));
    }

    @Test void representativeListPlansRetainLimitsInSql() throws Exception {
        var f=fixture("page-plan",1);
        bookings.createBooking(ADMIN,"page-plan-booking",f.user.getUserId(),f.vehicle.getVehicleId(),f.branchId,f.offeringId,
                LocalDateTime.of(2089,1,17,10,0),null);
        var plans=new java.util.ArrayList<java.util.Map<String,Object>>();
        for(String sql:java.util.List.of(
                "select booking_id from bookings where branch_id='page-plan-branch' order by scheduled_at, scheduled_at_nano_remainder, booking_id collate \"C\" limit 20",
                "select vehicle_id from vehicles v where exists(select 1 from bookings bk join branches b on b.branch_id=bk.branch_id where bk.vehicle_id=v.vehicle_id and b.business_id='page-plan-business') order by vehicle_id collate \"C\" limit 20")) {
            String plan=jdbc.queryForObject("explain (analyze, buffers, format json) "+sql,String.class);
            assertTrue(plan.contains("Limit"));
            // Only structural plan data is exported; no query/filter literals or customer data.
            var nodes=java.util.regex.Pattern.compile("\"Node Type\"\\s*:\\s*\"([^\"]+)\"").matcher(plan);
            var types=new java.util.ArrayList<String>();while(nodes.find())types.add(nodes.group(1));
            plans.add(java.util.Map.of("nodeTypes",types));
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/pagination-query-plans.txt"),plans.toString());
    }

    @Test void databaseOutageFailsReadinessWhileLivenessAndProbeAccessRemainIndependent() throws Exception {
        managementMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health/readiness"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        POSTGRES.getDockerClient().pauseContainerCmd(POSTGRES.getContainerId()).exec();
        try {
            managementMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health/readiness")
                            .header("Authorization", "Bearer invalid-outage-token"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("{\"status\":\"DOWN\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.components").doesNotExist());
            managementMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health/liveness"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        } finally {
            POSTGRES.getDockerClient().unpauseContainerCmd(POSTGRES.getContainerId()).exec();
        }
        managementMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health/readiness"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @Test void queueTelemetryReportsDatabaseWideCommittedStateWithoutHydratingRecords() {
        Fixture fixture = fixture("telemetry-depth", 1);
        Booking booking = createConfirmed(fixture, "telemetry-depth-booking", "telemetry-depth-queued-user",
                "telemetry-depth-queued-vehicle", "TELEMETRY-1", LocalDateTime.of(2089, 1, 17, 10, 0));
        queues.createQueueEntry(ADMIN, "telemetry-depth-entry", booking.getBookingId(), fixture.service.getServiceId());
        telemetrySampler.refresh();
        Long expected = jdbc.queryForObject("select count(*) from queue_entries where queue_status in ('WAITING','CALLED','IN_PROGRESS')", Long.class);
        assertEquals(expected.doubleValue(), telemetryMeters.get("carwash.queue.depth").gauge().value());
        assertTrue(expected >= 1);
    }

    @Test void rolledBackBookingNeverIncrementsCommittedOutcome() {
        Fixture fixture = fixture("telemetry-rollback", 1);
        double committed = telemetryOutcome("committed");
        double rollback = telemetryOutcome("rollback");
        assertThrows(IllegalStateException.class, () -> transactions.write(() -> {
            bookings.createBooking(ADMIN, "telemetry-rollback-booking", fixture.user.getUserId(),
                    fixture.vehicle.getVehicleId(), fixture.branchId, fixture.offeringId,
                    LocalDateTime.of(2089, 1, 17, 10, 0), null);
            throw new IllegalStateException("force outer rollback");
        }));
        assertFalse(bookingRepository.existsById("telemetry-rollback-booking"));
        assertEquals(committed, telemetryOutcome("committed"));
        assertEquals(rollback + 1, telemetryOutcome("rollback"));
    }

    private double telemetryOutcome(String outcome) {
        var counter = telemetryMeters.find("carwash.workflow.outcomes")
                .tags("operation", "booking_create", "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }
    @Autowired UserManagementService users;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired TenantMembershipManagementService tenantMemberships;
    @Autowired TenantMembershipRepository tenantMembershipRepository;
    @Autowired VehicleManagementService vehicles;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired MarketplaceManagementService marketplace;
    @Autowired BranchSchedulingService scheduling;
    @Autowired ServiceCatalogService catalog;
    @Autowired ServiceOfferingService offerings;
    @Autowired ServiceRepository serviceRepository;
    @Autowired ServiceOfferingRepository offeringRepository;
    @Autowired ServiceOfferingCapacityQuery offeringCapacityQuery;
    @Autowired CarWashBusinessRepository businessRepository;
    @Autowired CarWashBranchRepository branchRepository;
    @Autowired BranchOperatingScheduleRepository scheduleRepository;
    @Autowired TemporaryBranchClosureRepository closureRepository;
    @Autowired BookingManagementService bookings;
    @Autowired BookingRepository bookingRepository;
    @Autowired QueueManagementService queues;
    @Autowired QueueEntryRepository queueRepository;
    @Autowired NotificationManagementService notifications;
    @Autowired NotificationRepository notificationRepository;
    @Autowired DataTransactionOperations transactions;
    @Autowired AuditOperations audit;
    @Autowired AuditRepository auditRepository;

    @AfterEach void cleanMutableData(){jdbc.execute("truncate table audit_records, users, businesses, service_definitions cascade");}

    @Test void emptyDatabaseMigratesAndValidatesToLatest(){
        assertEquals("6",flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(6,jdbc.queryForObject("select count(*) from flyway_schema_history where success",Integer.class));
        assertEquals(4,jdbc.queryForObject("select count(*) from roles",Integer.class));
    }

    @Test void anEarlierMigrationUpgradesIncrementallyWithoutRecreatingData(){
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("create schema "+schema);
        try{
            Flyway first=Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("5").cleanDisabled(true).load();
            assertEquals(5,first.migrate().migrationsExecuted);
            try(var connection=POSTGRES.createConnection("");var statement=connection.createStatement()){
                statement.execute("insert into "+schema+".businesses(business_id,business_name,contact_email,contact_phone,business_status,registered_at,updated_at) values ('upgrade-business','Upgrade','upgrade@example.test','0123456789','ACTIVE',timestamp '2089-01-01',timestamp '2089-01-01')");
                statement.execute("insert into "+schema+".users(user_id,full_name,email,phone,account_status,created_at) values ('legacy-unassigned-staff','Legacy Staff','legacy-staff@example.test','0123456789','ACTIVE',timestamp '2089-01-01')");
                statement.execute("insert into "+schema+".user_credentials(user_id,encoded_password) values ('legacy-unassigned-staff','encoded')");
                statement.execute("insert into "+schema+".user_role_assignments(user_id,role_id) values ('legacy-unassigned-staff','builtin:STAFF')");
                statement.execute("insert into "+schema+".notifications(notification_id,user_id,notification_type,message,channel,sent_at,delivery_status) values ('legacy-notification','legacy-unassigned-staff','LEGACY','preserved','IN_APP',timestamp '2089-01-01','SENT')");
            }
            Flyway latest=Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").cleanDisabled(true).load();
            assertEquals(1,latest.migrate().migrationsExecuted);
            assertTrue(latest.validateWithResult().validationSuccessful);
            assertEquals(1,jdbc.queryForObject("select count(*) from "+schema+".businesses where business_id='upgrade-business'",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from "+schema+".tenant_memberships where user_id='legacy-unassigned-staff'",Integer.class));
            assertEquals(1,jdbc.queryForObject("select count(*) from pg_indexes where schemaname='"+schema+"' and indexname='ix_bookings_branch_status_time_tenant'",Integer.class));
            assertEquals(4,jdbc.queryForObject("select count(*) from pg_indexes where schemaname='"+schema+"' and indexname like 'ix_audit_%_date'",Integer.class));
            assertEquals(2,jdbc.queryForObject("select count(*) from "+schema+".role_permissions where permission='AUDIT_READ'",Integer.class));
            assertEquals(3,jdbc.queryForObject("select count(*) from pg_indexes where schemaname='"+schema+"' and indexname like 'ix_notifications_%_cursor'",Integer.class));
            assertEquals(1,jdbc.queryForObject("select count(*) from "+schema+".notifications where notification_id='legacy-notification'",Integer.class));
        }catch(Exception failure){throw new AssertionError(failure);}finally{jdbc.execute("drop schema "+schema+" cascade");}
    }

    @Test void notificationLifecycleSchemaIndexesPredicatesAndExactKeysetsAreDurable() {
        Fixture fixture=fixture("notification-inbox",3);
        LocalDateTime sentAt=LocalDateTime.of(2089,1,17,9,0,0,123456000);
        int bookingOrdinal=0;
        for(int remainder:new int[]{100,500,900}){
            Notification notification=new Notification("inbox-notification-"+remainder,fixture.user,null,
                    "INBOX_"+remainder,"bounded message","IN_APP");
            notification.setBranchId(fixture.branchId);
            notification.setServiceOfferingId(fixture.offeringId);
            // Operational scope is all-or-none, so use a canonical booking for persisted tenant context.
            Booking booking=bookings.createBooking(ADMIN,"inbox-booking-"+remainder,
                    fixture.user.getUserId(),fixture.vehicle.getVehicleId(),fixture.branchId,fixture.offeringId,
                    LocalDateTime.of(2089,1,18,10,0).plusMinutes(30L*bookingOrdinal++),null);
            notification.setBooking(booking);
            notification.send(sentAt.plusNanos(remainder));
            assertTrue(notificationRepository.insert(notification));
        }

        var inbox=notificationRepository.findInboxByUserIdAndBusinessId(
                fixture.user.getUserId(),"notification-inbox-business",false,null,2);
        var page=inbox.notifications();
        assertEquals(List.of("inbox-notification-900","inbox-notification-500"),
                page.stream().map(value->value.notificationId()).toList());
        assertEquals(3,inbox.unreadCount());
        var next=notificationRepository.findInboxByUserIdAndBusinessId(
                fixture.user.getUserId(),"notification-inbox-business",false,
                new NotificationCursor(page.getLast().sentAt(),page.getLast().notificationId()),2).notifications();
        assertEquals(List.of("inbox-notification-100"),
                next.stream().map(value->value.notificationId()).toList());
        var emptyTenant=notificationRepository.findInboxByUserIdAndBusinessId(
                fixture.user.getUserId(),"foreign-business",false,null,10);
        assertTrue(emptyTenant.notifications().isEmpty());
        assertEquals(0,emptyTenant.unreadCount());
        var emptyPage=notificationRepository.findInboxByUserIdAndBusinessId(
                fixture.user.getUserId(),"notification-inbox-business",true,
                new NotificationCursor(sentAt.minusSeconds(1),"after-all"),10);
        assertTrue(emptyPage.notifications().isEmpty());
        assertEquals(3,emptyPage.unreadCount());
        assertEquals(3,notificationRepository.countUnreadByUserIdAndBusinessId(
                fixture.user.getUserId(),"notification-inbox-business"));

        assertEquals(3,jdbc.queryForObject("select count(*) from pg_indexes where schemaname=current_schema() and indexname like 'ix_notifications_%_cursor'",Integer.class));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "update notifications set delivery_status='READ',read_at=null where notification_id=?",
                "inbox-notification-900"));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "update notifications set read_at=current_timestamp where notification_id=?",
                "inbox-notification-500"));
    }

    @Test void postgresRecipientReadMutationsAreIdempotentAtomicAndConcurrentSafe() throws Exception {
        Fixture fixture=fixture("notification-read",3);
        Booking booking=bookings.confirmBooking(ADMIN,bookings.createBooking(ADMIN,
                "notification-read-booking",fixture.user.getUserId(),fixture.vehicle.getVehicleId(),
                fixture.branchId,fixture.offeringId,LocalDateTime.of(2089,1,17,10,0),null).getBookingId());
        notifications.publishForAuthorizedBooking(booking,"SECOND_UNREAD","second");
        List<com.carwash.notification.domain.NotificationSnapshot> initial=
                notificationRepository.findPageByUserId(fixture.user.getUserId(),false,null,10);
        String notificationId=initial.getFirst().notificationId();
        TenantAccessContext customer=new TenantAccessContext(fixture.user.getUserId(),RoleName.CUSTOMER,null);
        CyclicBarrier start=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)){
            Future<com.carwash.notification.domain.NotificationSnapshot> first=executor.submit(()->{
                start.await();return notifications.markAsRead(customer,notificationId);
            });
            Future<com.carwash.notification.domain.NotificationSnapshot> second=executor.submit(()->{
                start.await();return notifications.markAsRead(customer,notificationId);
            });
            assertEquals(first.get().readAt(),second.get().readAt());
        }
        LocalDateTime original=notificationRepository.findSnapshotByIdAndUserId(
                notificationId,fixture.user.getUserId()).orElseThrow().readAt();
        assertEquals(original,notifications.markAsRead(customer,notificationId).readAt());

        var all=notifications.markAllAsRead(customer,fixture.user.getUserId());
        assertEquals(1,all.affectedCount());
        assertEquals(0,notifications.markAllAsRead(customer,fixture.user.getUserId()).affectedCount());
        assertEquals(0,notificationRepository.countUnreadByUserId(fixture.user.getUserId()));
        assertThrows(ResourceNotFoundException.class,()->notifications.markAsRead(
                new TenantAccessContext("foreign-customer",RoleName.CUSTOMER,null),notificationId));
    }

    @Test void concurrentMarkOneCommitCannotContradictPostgresInboxSnapshot() throws Exception {
        Fixture fixture=fixture("notification-snapshot-one",3);
        Booking booking=bookings.confirmBooking(ADMIN,bookings.createBooking(ADMIN,
                "notification-snapshot-one-booking",fixture.user.getUserId(),fixture.vehicle.getVehicleId(),
                fixture.branchId,fixture.offeringId,LocalDateTime.of(2089,1,17,10,0),null).getBookingId());
        String notificationId=notificationRepository.findByBookingId(booking.getBookingId())
                .getFirst().getNotificationId();
        CountDownLatch updateApplied=new CountDownLatch(1);
        CountDownLatch allowCommit=new CountDownLatch(1);

        try(var executor=Executors.newSingleThreadExecutor()){
            Future<?> writer=executor.submit(()->{
                try(var connection=POSTGRES.createConnection("");var statement=connection.prepareStatement("""
                        update notifications set delivery_status='READ',read_at=current_timestamp,
                            read_at_nano_remainder=0,version=version+1
                        where notification_id=? and user_id=? and delivery_status='SENT'
                        """)){
                    connection.setAutoCommit(false);
                    statement.setString(1,notificationId);
                    statement.setString(2,fixture.user.getUserId());
                    assertEquals(1,statement.executeUpdate());
                    updateApplied.countDown();
                    assertTrue(allowCommit.await(5,TimeUnit.SECONDS));
                    connection.commit();
                }
                return null;
            });
            assertTrue(updateApplied.await(5,TimeUnit.SECONDS));
            var snapshot=notificationRepository.findInboxByUserId(
                    fixture.user.getUserId(),false,null,10);
            allowCommit.countDown();
            writer.get(5,TimeUnit.SECONDS);

            assertEquals(DeliveryStatus.SENT,snapshot.notifications().getFirst().deliveryStatus());
            assertEquals(1,snapshot.unreadCount());
            assertEquals(0,notificationRepository.findInboxByUserId(
                    fixture.user.getUserId(),false,null,10).unreadCount());
        }finally{
            allowCommit.countDown();
        }
    }

    @Test void concurrentMarkAllCommitCannotContradictPostgresInboxSnapshot() throws Exception {
        Fixture fixture=fixture("notification-snapshot-all",3);
        Booking booking=bookings.confirmBooking(ADMIN,bookings.createBooking(ADMIN,
                "notification-snapshot-all-booking",fixture.user.getUserId(),fixture.vehicle.getVehicleId(),
                fixture.branchId,fixture.offeringId,LocalDateTime.of(2089,1,17,10,0),null).getBookingId());
        notifications.publishForAuthorizedBooking(booking,"SECOND_UNREAD","second");
        CountDownLatch updateApplied=new CountDownLatch(1);
        CountDownLatch allowCommit=new CountDownLatch(1);

        try(var executor=Executors.newSingleThreadExecutor()){
            Future<?> writer=executor.submit(()->{
                try(var connection=POSTGRES.createConnection("");var statement=connection.prepareStatement("""
                        update notifications set delivery_status='READ',read_at=current_timestamp,
                            read_at_nano_remainder=0,version=version+1
                        where user_id=? and delivery_status='SENT'
                        """)){
                    connection.setAutoCommit(false);
                    statement.setString(1,fixture.user.getUserId());
                    assertEquals(2,statement.executeUpdate());
                    updateApplied.countDown();
                    assertTrue(allowCommit.await(5,TimeUnit.SECONDS));
                    connection.commit();
                }
                return null;
            });
            assertTrue(updateApplied.await(5,TimeUnit.SECONDS));
            var snapshot=notificationRepository.findInboxByUserId(
                    fixture.user.getUserId(),true,null,10);
            allowCommit.countDown();
            writer.get(5,TimeUnit.SECONDS);

            assertEquals(2,snapshot.notifications().size());
            assertTrue(snapshot.notifications().stream()
                    .allMatch(notification->notification.deliveryStatus()==DeliveryStatus.SENT));
            assertEquals(2,snapshot.unreadCount());
            var after=notificationRepository.findInboxByUserId(
                    fixture.user.getUserId(),true,null,10);
            assertTrue(after.notifications().isEmpty());
            assertEquals(0,after.unreadCount());
        }finally{
            allowCommit.countDown();
        }
    }

    @Test void auditSchemaConstraintsIndexesAtomicityAndConcurrentAppendsAreDurable() throws Exception {
        assertEquals(4,jdbc.queryForObject("select count(*) from pg_indexes where schemaname=current_schema() and indexname like 'ix_audit_%_date'",Integer.class));
        assertEquals(2,jdbc.queryForObject("select count(*) from role_permissions where permission='AUDIT_READ'",Integer.class));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("""
                insert into audit_records(audit_id,occurred_at,actor_type,action,target_type,outcome,
                    reason_code,correlation_id,metadata,event_source)
                values ('invalid-audit',current_timestamp,'ANONYMOUS','LOGIN_FAILURE','AUTHENTICATION',
                    'FAILURE','INVALID','invalid-correlation','[]'::jsonb,'SECURITY')
                """));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("""
                insert into audit_records(audit_id,occurred_at,actor_type,action,target_type,outcome,
                    correlation_id,metadata,event_source)
                values ('unsafe-key-audit',current_timestamp,'SYSTEM','PLATFORM_ADMIN_OPERATION','DATABASE',
                    'SUCCESS','unsafe-key-correlation','{"password":"must-not-persist"}'::jsonb,'SYSTEM')
                """));

        Instant boundary=Instant.parse("2089-01-15T12:00:00.123456000Z");
        for(int remainder:new int[]{100,500,900}){
            auditRepository.append(new AuditRecord("nano-audit-"+remainder,boundary.plusNanos(remainder),
                    com.carwash.audit.domain.AuditActorType.SYSTEM,null,null,"nano-business",
                    AuditAction.PLATFORM_ADMIN_OPERATION,"NANO_BOUNDARY","nano-"+remainder,
                    AuditOutcome.SUCCESS,null,"nano-correlation-"+remainder,java.util.Map.of(),AuditSource.SYSTEM));
        }
        List<AuditRecord> exactNanos=auditRepository.queryByBusinessId("nano-business",new AuditQuery(
                null,AuditAction.PLATFORM_ADMIN_OPERATION,null,"NANO_BOUNDARY",null,
                boundary.plusNanos(200),boundary.plusNanos(800),null,10));
        assertEquals(List.of("nano-audit-500"),exactNanos.stream().map(AuditRecord::auditId).toList());

        marketplace.registerBusiness(ADMIN,new RegisterBusinessCommand(
                "audit-atomic-business","Original Audit Business","audit-atomic@example.test",
                "0123456789","audit-atomic-registration"));
        jdbc.execute("""
                create function reject_business_update_audit() returns trigger language plpgsql as $$
                begin
                    if new.action = 'BUSINESS_UPDATED' then
                        raise exception 'injected mandatory audit failure';
                    end if;
                    return new;
                end $$
                """);
        jdbc.execute("create trigger reject_business_update_audit before insert on audit_records for each row execute function reject_business_update_audit()");
        try {
            assertThrows(RuntimeException.class,()->marketplace.updateBusiness(ADMIN,"audit-atomic-business",
                    new UpdateBusinessCommand("Must Roll Back","audit-atomic@example.test",
                            "0123456789","audit-atomic-registration")));
            assertEquals("Original Audit Business",marketplace.findBusiness("audit-atomic-business").businessName());
        } finally {
            jdbc.execute("drop trigger reject_business_update_audit on audit_records");
            jdbc.execute("drop function reject_business_update_audit()");
        }

        int count=20;
        CountDownLatch start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(5)){
            List<Future<Void>> futures=java.util.stream.IntStream.range(0,count)
                    .mapToObj(index->executor.<Void>submit(()->{
                start.await();
                AuditRequestContext.open("postgres-audit-"+index);
                try{
                    audit.appendIsolated(AuditCommand.actionForBusiness(AuditAction.PLATFORM_ADMIN_OPERATION,
                            AuditActor.system(),"audit-atomic-business","PG_CONCURRENT","target-"+index,
                            AuditSource.SYSTEM),AuditOutcome.SUCCESS,null);
                }finally{AuditRequestContext.close();}
                return null;
            })).toList();
            start.countDown();
            for(Future<Void> future:futures)future.get();
        }
        assertEquals(count,jdbc.queryForObject(
                "select count(*) from audit_records where target_type='PG_CONCURRENT'",Integer.class));
        assertEquals(count,jdbc.queryForObject(
                "select count(distinct audit_id) from audit_records where target_type='PG_CONCURRENT'",Integer.class));
    }

    @Test void failedBusinessTransactionLeavesOnlyItsIsolatedFailureAudit() {
        AuditRequestContext.open("postgres-rollback-audit");
        try {
            AuditCommand command=AuditCommand.actionForBusiness(AuditAction.PLATFORM_ADMIN_OPERATION,
                    AuditActor.system(),"rollback-audit-business","PG_ROLLBACK","rollback-audit-target",
                    AuditSource.SYSTEM);
            assertThrows(IllegalStateException.class,()->audit.execute(command,()->{
                jdbc.update("""
                        insert into businesses(business_id,business_name,contact_email,contact_phone,
                            business_status,registered_at,updated_at)
                        values ('rollback-audit-business','Rollback','rollback-audit@example.test',
                            '0123456789','ACTIVE',current_timestamp,current_timestamp)
                        """);
                throw new IllegalStateException("injected protected failure");
            }));
        } finally {
            AuditRequestContext.close();
        }
        assertEquals(0,jdbc.queryForObject(
                "select count(*) from businesses where business_id='rollback-audit-business'",Integer.class));
        List<com.carwash.audit.domain.AuditRecord> failure=auditRepository.queryByBusinessId(
                "rollback-audit-business",new AuditQuery(null,AuditAction.PLATFORM_ADMIN_OPERATION,null,
                        "PG_ROLLBACK",null,Instant.now().minusSeconds(60),Instant.now().plusSeconds(60),null,10));
        assertEquals(1,failure.size());
        assertEquals(AuditOutcome.FAILURE,failure.getFirst().outcome());
        assertEquals("OPERATION_FAILED",failure.getFirst().reasonCode());
    }

    @Test void bookingAuditScopeUsesCanonicalTenantAndRejectedSuccessAppendPreventsMutation() {
        Fixture tenantA=fixture("booking-audit-a",3);
        Fixture tenantB=fixture("booking-audit-b",3);
        TenantAccessContext customerA=new TenantAccessContext(
                tenantA.user.getUserId(),RoleName.CUSTOMER,null);
        TenantAccessContext customerB=new TenantAccessContext(
                tenantB.user.getUserId(),RoleName.CUSTOMER,null);
        LocalDateTime originalTime=LocalDateTime.of(2089,1,17,10,0);

        Booking booking=bookings.createBooking(customerA,"booking-audit-customer-booking",
                tenantA.user.getUserId(),tenantA.vehicle.getVehicleId(),tenantA.branchId,
                tenantA.offeringId,originalTime,"must never enter audit metadata");

        List<AuditRecord> created=auditRepository.queryByBusinessId(
                "booking-audit-a-business",new AuditQuery(
                        tenantA.user.getUserId(),AuditAction.BOOKING_CREATED,AuditOutcome.SUCCESS,
                        "BOOKING",booking.getBookingId(),Instant.EPOCH,Instant.parse("2100-01-01T00:00:00Z"),
                        null,10));
        assertEquals(1,created.size());
        assertEquals("booking-audit-a-business",created.getFirst().businessId());
        assertEquals("CUSTOMER",created.getFirst().actorRole());
        assertTrue(auditRepository.queryByBusinessId(
                "booking-audit-b-business",new AuditQuery(
                        null,AuditAction.BOOKING_CREATED,null,"BOOKING",booking.getBookingId(),
                        Instant.EPOCH,Instant.parse("2100-01-01T00:00:00Z"),null,10)).isEmpty());

        bookings.updateBooking(ADMIN,booking.getBookingId(),tenantA.vehicle.getVehicleId(),
                tenantA.offeringId,"administrator mutation");
        List<AuditRecord> administratorUpdate=auditRepository.queryByBusinessId(
                "booking-audit-a-business",new AuditQuery(
                        ADMIN.userId(),AuditAction.BOOKING_UPDATED,AuditOutcome.SUCCESS,"BOOKING",
                        booking.getBookingId(),Instant.EPOCH,Instant.parse("2100-01-01T00:00:00Z"),null,10));
        assertEquals(1,administratorUpdate.size());
        assertEquals("booking-audit-a-business",administratorUpdate.getFirst().businessId());
        assertEquals("PLATFORM_ADMIN",administratorUpdate.getFirst().actorRole());

        assertThrows(ResourceNotFoundException.class,()->bookings.updateBooking(
                customerB,booking.getBookingId(),tenantB.vehicle.getVehicleId(),tenantB.offeringId,"forbidden"));
        assertEquals(1,jdbc.queryForObject("""
                select count(*) from audit_records
                where action='BOOKING_UPDATED' and target_id=? and outcome='DENIED'
                  and actor_user_id=? and business_id is null
                """,Integer.class,booking.getBookingId(),tenantB.user.getUserId()));

        jdbc.execute("""
                create function reject_booking_reschedule_success_audit() returns trigger language plpgsql as $$
                begin
                    if new.action = 'BOOKING_RESCHEDULED' and new.outcome = 'SUCCESS' then
                        raise exception 'injected booking audit failure';
                    end if;
                    return new;
                end $$
                """);
        jdbc.execute("""
                create trigger reject_booking_reschedule_success_audit
                before insert on audit_records for each row
                execute function reject_booking_reschedule_success_audit()
                """);
        try {
            assertThrows(RuntimeException.class,()->bookings.rescheduleBooking(
                    customerA,booking.getBookingId(),originalTime.plusHours(1)));
            assertEquals(originalTime,bookings.findById(booking.getBookingId()).getScheduledDateTime());
            assertEquals(1,jdbc.queryForObject("""
                    select count(*) from audit_records
                    where action='BOOKING_RESCHEDULED' and target_id=? and outcome='FAILURE'
                      and actor_user_id=? and business_id is null
                    """,Integer.class,booking.getBookingId(),tenantA.user.getUserId()));
            assertEquals(0,jdbc.queryForObject("""
                    select count(*) from audit_records
                    where action='BOOKING_RESCHEDULED' and target_id=? and outcome='SUCCESS'
                    """,Integer.class,booking.getBookingId()));
        } finally {
            jdbc.execute("drop trigger reject_booking_reschedule_success_audit on audit_records");
            jdbc.execute("drop function reject_booking_reschedule_success_audit()");
        }
    }

    @Test void membershipConstraintsAndTransactionsEnforceOneOperationalTenant(){
        Fixture fixture=fixture("membership",2);
        marketplace.registerBusiness(ADMIN,new RegisterBusinessCommand(
                "membership-other-business","Other Membership Business",
                "membership-other@example.test","0123456789","membership-other-registration"));
        tenantMemberships.assignRole(fixture.user.getUserId(),RoleName.STAFF,"membership-business");
        assertEquals("membership-business",tenantMembershipRepository.findById(fixture.user.getUserId())
                .orElseThrow().businessId());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "insert into tenant_memberships(user_id,business_id,assigned_at) values (?,?,current_timestamp)",
                fixture.user.getUserId(),"membership-other-business"));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "update user_role_assignments set role_id='builtin:CUSTOMER' where user_id=?",
                fixture.user.getUserId()));

        User rollback=createUser("membership-rollback","membership-rollback@example.test");
        assertThrows(IllegalStateException.class,()->transactions.write(()->{
            tenantMemberships.assignRole(rollback.getUserId(),RoleName.BUSINESS_OWNER,"membership-business");
            throw new IllegalStateException("injected");
        }));
        assertTrue(tenantMembershipRepository.findById(rollback.getUserId()).isEmpty());
        assertEquals(RoleName.CUSTOMER,com.carwash.identity.domain.RoleCatalog.name(
                userRepository.findById(rollback.getUserId()).orElseThrow().getRole()));
    }

    @Test void tenantPredicatesAndRelationalConstraintsRejectCrossBusinessScope(){
        Fixture first=fixture("tenant-a",2);
        Fixture second=fixture("tenant-b",2);
        Booking firstBooking=bookings.confirmBooking(ADMIN,bookings.createBooking(ADMIN,
                "tenant-a-booking",first.user.getUserId(),first.vehicle.getVehicleId(),first.branchId,
                first.offeringId,LocalDateTime.of(2089,1,17,10,0),null).getBookingId());
        QueueEntry firstQueue=queues.createQueueEntry(ADMIN,
                "tenant-a-queue",firstBooking.getBookingId(),first.service.getServiceId());

        assertEquals(List.of(firstBooking.getBookingId()),bookingRepository.findByBusinessId("tenant-a-business")
                .stream().map(Booking::getBookingId).toList());
        assertTrue(bookingRepository.findByIdAndBusinessId(
                firstBooking.getBookingId(),"tenant-b-business").isEmpty());
        assertEquals(List.of(firstQueue.getQueueEntryId()),queueRepository.findByBusinessId("tenant-a-business")
                .stream().map(QueueEntry::getQueueEntryId).toList());
        assertTrue(queueRepository.findByIdAndBusinessId(
                firstQueue.getQueueEntryId(),"tenant-b-business").isEmpty());
        assertTrue(offeringRepository.findByIdAndBusinessId(
                first.offeringId,"tenant-b-business").isEmpty());
        assertTrue(branchRepository.findByIdAndBusinessId(
                first.branchId,"tenant-b-business").isEmpty());
        assertTrue(scheduleRepository.findByBranchIdAndBusinessId(
                first.branchId,"tenant-b-business").isEmpty());

        String notificationId=notificationRepository.findByBookingId(firstBooking.getBookingId())
                .getFirst().getNotificationId();
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "update notifications set branch_id=?,offering_id=? where notification_id=?",
                second.branchId,second.offeringId,notificationId));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "update notifications set branch_id=null where notification_id=?",notificationId));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "insert into bookings(booking_id,user_id,vehicle_id,branch_id,offering_id,service_id,scheduled_at,booking_status,created_at) values (?,?,?,?,?,?,timestamp '2089-01-17 11:00:00','CREATED',timestamp '2089-01-01')",
                "tenant-mismatch-booking",first.user.getUserId(),first.vehicle.getVehicleId(),
                first.branchId,second.offeringId,second.service.getServiceId()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "update queue_entries set branch_id=?,offering_id=?,service_id=? where queue_entry_id=?",
                second.branchId,second.offeringId,second.service.getServiceId(),firstQueue.getQueueEntryId()));
    }

    @Test void tenantMutationWaitsForResourceLockAndRejectsARecreatedForeignBranch() throws Exception {
        marketplace.registerBusiness(ADMIN,new RegisterBusinessCommand(
                "atomic-business-a","Atomic A","atomic-a@example.test","0123456789","atomic-reg-a"));
        marketplace.registerBusiness(ADMIN,new RegisterBusinessCommand(
                "atomic-business-b","Atomic B","atomic-b@example.test","0123456789","atomic-reg-b"));
        marketplace.createBranch(ADMIN,"atomic-business-a",new CreateBranchCommand(
                "atomic-branch","Original A","1 Main",null,"Cape Town","Western Cape","8001","ZA",
                BigDecimal.ZERO,BigDecimal.ZERO,"UTC",true));
        TenantAccessContext tenantA=new TenantAccessContext(
                "atomic-owner-a",RoleName.BUSINESS_OWNER,"atomic-business-a");
        CountDownLatch replacementHoldingLock=new CountDownLatch(1);
        CountDownLatch allowReplacementCommit=new CountDownLatch(1);

        try(var executor=Executors.newFixedThreadPool(2)){
            Future<?> replacement=executor.submit(()->{
                try(var connection=POSTGRES.createConnection("");var statement=connection.createStatement()){
                    connection.setAutoCommit(false);
                    statement.execute("select pg_advisory_xact_lock(hashtextextended('07:branch:atomic-branch',0))");
                    statement.executeUpdate("delete from branches where branch_id='atomic-branch'");
                    statement.executeUpdate("insert into branches(branch_id,business_id,branch_name,address_line1,city,province,postal_code,country_code,latitude,longitude,timezone,branch_status,public_discovery_enabled,created_at,updated_at) values ('atomic-branch','atomic-business-b','Canonical B','2 Main','Cape Town','Western Cape','8001','ZA',0,0,'UTC','ACTIVE',true,current_timestamp,current_timestamp)");
                    replacementHoldingLock.countDown();
                    assertTrue(allowReplacementCommit.await(5,TimeUnit.SECONDS));
                    connection.commit();
                }
                return null;
            });
            assertTrue(replacementHoldingLock.await(5,TimeUnit.SECONDS));
            Future<?> mutation=executor.submit(()->marketplace.updateBranch(
                    tenantA,"atomic-branch",new UpdateBranchCommand(
                            "Tenant A overwrite","3 Main",null,"Cape Town","Western Cape","8001","ZA",
                            BigDecimal.ZERO,BigDecimal.ZERO,"UTC",true)));

            try{
                awaitAdvisoryLockWaiter();
            }finally{
                allowReplacementCommit.countDown();
            }
            replacement.get(5,TimeUnit.SECONDS);
            ExecutionException failure=assertThrows(ExecutionException.class,
                    ()->mutation.get(5,TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof ResourceNotFoundException);
        }

        assertEquals("atomic-business-b",jdbc.queryForObject(
                "select business_id from branches where branch_id='atomic-branch'",String.class));
        assertEquals("Canonical B",jdbc.queryForObject(
                "select branch_name from branches where branch_id='atomic-branch'",String.class));
    }

    @Test void repositoryContractsPreserveDuplicateMissingAndCaseInsensitiveUniqueness(){
        User first=createUser("contract-user","Contract@Example.test");
        assertFalse(userRepository.insert(first));
        User missing=createUserValue("missing-user","missing@example.test");
        assertFalse(userRepository.update(missing));
        BusinessRuleViolationException duplicate=assertThrows(BusinessRuleViolationException.class,()->createUser("other-user","contract@example.TEST"));
        assertEquals("User email already exists",duplicate.getMessage());
        vehicles.createVehicle(ADMIN,"contract-user","vehicle-one"," CA 123 ","SUV","A","B","C",null);
        assertThrows(BusinessRuleViolationException.class,()->vehicles.createVehicle(
                ADMIN,"contract-user","vehicle-two","ca 123","SUV","A","B","C",null));
        String maximumPhone="+"+"1".repeat(39);
        users.createUser(new CreateUserCommand("phone-user","Phone User","phone@example.test",maximumPhone,PASSWORD));
        assertEquals(maximumPhone,userRepository.findById("phone-user").orElseThrow().getPhone());
        marketplace.registerBusiness(ADMIN,new RegisterBusinessCommand("registration-one","First","first-business@example.test","0123456789","REGISTRATION-001"));
        BusinessRuleViolationException registrationDuplicate=assertThrows(BusinessRuleViolationException.class,()->marketplace.registerBusiness(ADMIN,new RegisterBusinessCommand("registration-two","Second","second-business@example.test","0123456789","registration-001")));
        assertEquals("Business registration number already exists",registrationDuplicate.getMessage());
    }

    @Test void everyPostgresAdapterHonorsDuplicateInsertAndDeterministicReadContracts(){
        Fixture fixture=fixture("contracts",2);
        Booking booking=bookings.confirmBooking(ADMIN,bookings.createBooking(ADMIN,"contracts-booking",fixture.user.getUserId(),fixture.vehicle.getVehicleId(),fixture.branchId,fixture.offeringId,LocalDateTime.of(2089,1,17,10,0),null).getBookingId());
        QueueEntry queue=queues.createQueueEntry(ADMIN,"contracts-queue",booking.getBookingId(),fixture.service.getServiceId());
        scheduling.createTemporaryClosure(ADMIN,fixture.branchId,new com.carwash.marketplace.application.CreateTemporaryBranchClosureCommand("contracts-closure",Instant.parse("2089-01-18T10:00:00Z"),Instant.parse("2089-01-18T11:00:00Z"),"contract"));

        assertFalse(roleRepository.insert(roleRepository.findById("builtin:CUSTOMER").orElseThrow()));
        assertFalse(userRepository.insert(fixture.user));
        assertFalse(vehicleRepository.insert(fixture.vehicle));
        assertFalse(businessRepository.insert(businessRepository.findById("contracts-business").orElseThrow()));
        assertFalse(branchRepository.insert(branchRepository.findById(fixture.branchId).orElseThrow()));
        assertFalse(serviceRepository.insert(fixture.service));
        assertFalse(offeringRepository.insert(offeringRepository.findById(fixture.offeringId).orElseThrow()));
        assertFalse(scheduleRepository.insert(scheduleRepository.findById(fixture.branchId).orElseThrow()));
        assertFalse(closureRepository.insert(closureRepository.findById("contracts-closure").orElseThrow()));
        assertFalse(bookingRepository.insert(bookingRepository.findById(booking.getBookingId()).orElseThrow()));
        assertFalse(queueRepository.insert(queueRepository.findById(queue.getQueueEntryId()).orElseThrow()));
        var notification=notificationRepository.findByBookingId(booking.getBookingId()).getFirst();
        assertFalse(notificationRepository.insert(notification));

        assertEquals(roleRepository.findAll().stream().map(role->role.getRoleId()).sorted().toList(),roleRepository.findAll().stream().map(role->role.getRoleId()).toList());
        assertEquals(bookingRepository.findAll().stream().map(Booking::getBookingId).sorted().toList(),bookingRepository.findAll().stream().map(Booking::getBookingId).toList());
        assertFalse(serviceRepository.update(service("missing-service")));
    }

    @Test void databaseConstraintsRejectUnsafeDeletionAndDuplicateBranchOffering(){
        Fixture fixture=fixture("constraints",2);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("delete from users where user_id=?",fixture.user.getUserId()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("delete from businesses where business_id=?","constraints-business"));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("delete from service_definitions where service_id=?",fixture.service.getServiceId()));
        ServiceOffering duplicate=new ServiceOffering("constraints-other-offering",fixture.branchId,fixture.service.getServiceId(),new BigDecimal("90.00"),20,1,ServiceOfferingStatus.ACTIVE,LocalDateTime.of(2089,1,1,0,0),LocalDateTime.of(2089,1,1,0,0));
        assertThrows(BusinessRuleViolationException.class,()->offeringRepository.insert(duplicate));
    }

    @Test void thrownMidWorkflowFailureRollsBackEveryWrite(){
        assertThrows(IllegalStateException.class,()->transactions.write(()->{users.createUser(new CreateUserCommand("rollback-user","Rollback","rollback@example.test","0123456789",PASSWORD));throw new IllegalStateException("injected");}));
        assertTrue(userRepository.findById("rollback-user").isEmpty());
    }

    @Test void nanosecondPrecisionRoundTripsForSchedulesClosuresAndBookings(){
        Fixture fixture=fixture("nanos",2);
        LocalTime opens=LocalTime.of(8,0,0,123456789);LocalTime closes=LocalTime.of(18,0,0,987654321);
        scheduling.replaceOperatingSchedule(ADMIN,fixture.branchId,new ReplaceOperatingScheduleCommand(List.of(new WeeklyOperatingIntervalCommand(DayOfWeek.MONDAY,opens,closes))));
        var schedule=scheduling.getOperatingSchedule(fixture.branchId);
        assertEquals(opens,schedule.intervals().getFirst().opensAt());assertEquals(closes,schedule.intervals().getFirst().closesAt());
        Instant start=Instant.ofEpochSecond(4_000_000_000L,123456789);Instant end=start.plusSeconds(10).plusNanos(111);
        scheduling.createTemporaryClosure(ADMIN,fixture.branchId,new com.carwash.marketplace.application.CreateTemporaryBranchClosureCommand("nano-closure",start,end,"precision"));
        assertEquals(start,scheduling.listTemporaryClosures(fixture.branchId).getFirst().startAt());
        LocalDateTime at=LocalDateTime.of(2089,1,17,10,0,0,123456789);
        Booking value=new Booking("nano-booking",fixture.user,fixture.vehicle,fixture.branchId,fixture.offeringId,fixture.service,at,"precision");
        value.setCreatedAt(LocalDateTime.of(2089,1,1,0,0,0,987654321));assertTrue(bookingRepository.insert(value));
        assertEquals(at,bookingRepository.findById("nano-booking").orElseThrow().getScheduledDateTime());
        assertEquals(value.getCreatedAt(),bookingRepository.findById("nano-booking").orElseThrow().getCreatedAt());
    }

    @Test void concurrentCapacityOneBookingAttemptsAllowExactlyOneWinner() throws Exception {
        Fixture fixture=fixture("capacity",1);User second=createUser("capacity-user-2","capacity-2@example.test");Vehicle secondVehicle=vehicles.createVehicle(ADMIN,second.getUserId(),"capacity-vehicle-2","CAP-2","SUV","A","B","C",null);LocalDateTime at=LocalDateTime.of(2089,1,17,10,0);
        List<Callable<Booking>> work=List.of(()->bookings.createBooking(ADMIN,"capacity-booking-1",fixture.user.getUserId(),fixture.vehicle.getVehicleId(),fixture.branchId,fixture.offeringId,at,null),()->bookings.createBooking(ADMIN,"capacity-booking-2",second.getUserId(),secondVehicle.getVehicleId(),fixture.branchId,fixture.offeringId,at,null));
        try(var executor=Executors.newFixedThreadPool(2)){List<Future<Booking>> results=executor.invokeAll(work);int success=0;int failure=0;for(Future<Booking> result:results){try{result.get();success++;}catch(Exception ignored){failure++;}}assertEquals(1,success);assertEquals(1,failure);}
        assertEquals(1,bookingRepository.findByServiceOfferingId(fixture.offeringId).size());
    }

    @Test void concurrentCancellationAndCapacityDecisionRemainSerialized() throws Exception {
        Fixture fixture=fixture("cancel-capacity",1);LocalDateTime at=LocalDateTime.of(2089,1,17,10,0);Booking existing=bookings.createBooking(ADMIN,"cancel-existing",fixture.user.getUserId(),fixture.vehicle.getVehicleId(),fixture.branchId,fixture.offeringId,at,null);User other=createUser("cancel-other","cancel-other@example.test");Vehicle otherVehicle=vehicles.createVehicle(ADMIN,other.getUserId(),"cancel-other-vehicle","CANCEL-2","SUV","A","B","C",null);CyclicBarrier barrier=new CyclicBarrier(2);
        Callable<Booking> cancel=()->{barrier.await();return bookings.cancelBooking(ADMIN,existing.getBookingId());};Callable<Booking> create=()->{barrier.await();return bookings.createBooking(ADMIN,"cancel-replacement",other.getUserId(),otherVehicle.getVehicleId(),fixture.branchId,fixture.offeringId,at,null);};boolean created;
        try(var executor=Executors.newFixedThreadPool(2)){Future<Booking> cancelled=executor.submit(cancel);Future<Booking> replacement=executor.submit(create);assertEquals(BookingStatus.CANCELLED,cancelled.get().getStatus());try{replacement.get();created=true;}catch(Exception failure){created=false;}}
        if(!created)bookings.createBooking(ADMIN,"cancel-replacement",other.getUserId(),otherVehicle.getVehicleId(),fixture.branchId,fixture.offeringId,at,null);
        assertEquals(1,bookingRepository.findByServiceOfferingId(fixture.offeringId).stream().filter(value->value.getStatus()!=BookingStatus.CANCELLED&&value.getStatus()!=BookingStatus.COMPLETED).count());
    }

    @Test void offeringTermsCannotReduceCapacityBelowActiveOverlap(){
        Fixture fixture=fixture("offering-capacity",2);
        createConfirmed(fixture,"offering-capacity-booking-1","offering-capacity-user-1","offering-capacity-vehicle-1","OC-1",LocalDateTime.of(2089,1,17,10,0));
        createConfirmed(fixture,"offering-capacity-booking-2","offering-capacity-user-2","offering-capacity-vehicle-2","OC-2",LocalDateTime.of(2089,1,17,10,30));

        assertThrows(BusinessRuleViolationException.class,()->offerings.updateOffering(
                ADMIN,fixture.offeringId,new UpdateServiceOfferingCommand(new BigDecimal("100.00"),60,1)));
        assertEquals(30,offerings.findOffering(fixture.offeringId).estimatedDurationMin());
        assertEquals(2,offerings.findOffering(fixture.offeringId).concurrentCapacity());
    }

    @Test void concurrentOfferingReductionAndBookingCreationCannotOverbook() throws Exception {
        Fixture fixture=fixture("offering-race",2);
        bookings.createBooking(ADMIN,"offering-race-existing",fixture.user.getUserId(),fixture.vehicle.getVehicleId(),
                fixture.branchId,fixture.offeringId,LocalDateTime.of(2089,1,17,10,0),null);
        User other=createUser("offering-race-other","offering-race-other@example.test");
        Vehicle otherVehicle=vehicles.createVehicle(ADMIN,other.getUserId(),"offering-race-other-vehicle",
                "OR-2","SUV","A","B","C",null);
        CyclicBarrier barrier=new CyclicBarrier(2);
        Callable<Object> reduce=()->{barrier.await();return offerings.updateOffering(ADMIN,fixture.offeringId,
                new UpdateServiceOfferingCommand(new BigDecimal("100.00"),30,1));};
        Callable<Object> create=()->{barrier.await();return bookings.createBooking(ADMIN,"offering-race-new",
                other.getUserId(),otherVehicle.getVehicleId(),fixture.branchId,fixture.offeringId,
                LocalDateTime.of(2089,1,17,10,0),null);};

        try(var executor=Executors.newFixedThreadPool(2)){
            List<Future<Object>> results=executor.invokeAll(List.of(reduce,create));
            assertEquals(1,results.stream().filter(result->{try{result.get();return true;}catch(Exception failure){return false;}}).count());
        }
        int capacity=offerings.findOffering(fixture.offeringId).concurrentCapacity();
        assertTrue(offeringCapacityQuery.maximumConcurrentActiveBookings(fixture.offeringId,30)<=capacity);
    }

    @Test void concurrentReschedulingAndBookingCreationCannotOverbook() throws Exception {
        Fixture fixture=fixture("reschedule-capacity",1);
        Booking existing=bookings.createBooking(ADMIN,"reschedule-existing",fixture.user.getUserId(),
                fixture.vehicle.getVehicleId(),fixture.branchId,fixture.offeringId,
                LocalDateTime.of(2089,1,17,10,0),null);
        User other=createUser("reschedule-other","reschedule-other@example.test");
        Vehicle otherVehicle=vehicles.createVehicle(ADMIN,other.getUserId(),"reschedule-other-vehicle",
                "RESCHEDULE-2","SUV","A","B","C",null);
        CyclicBarrier barrier=new CyclicBarrier(2);
        Callable<Booking> reschedule=()->{barrier.await();return bookings.rescheduleBooking(
                ADMIN,existing.getBookingId(),LocalDateTime.of(2089,1,17,10,30));};
        Callable<Booking> create=()->{barrier.await();return bookings.createBooking(ADMIN,
                "reschedule-new",other.getUserId(),otherVehicle.getVehicleId(),fixture.branchId,
                fixture.offeringId,LocalDateTime.of(2089,1,17,10,0),null);};

        try(var executor=Executors.newFixedThreadPool(2)){
            Future<Booking> moved=executor.submit(reschedule);
            Future<Booking> added=executor.submit(create);
            assertEquals(LocalDateTime.of(2089,1,17,10,30),moved.get().getScheduledDateTime());
            try{added.get();}catch(Exception ignored){ }
        }
        assertTrue(offeringCapacityQuery.maximumConcurrentActiveBookings(fixture.offeringId,30)<=1);
    }

    @Test void concurrentQueueJoinsProduceUniqueContiguousBranchPositions() throws Exception {
        Fixture fixture=fixture("queue-join",2);Booking one=createConfirmed(fixture,"queue-booking-1","queue-user-1","queue-vehicle-1","QJ-1",LocalDateTime.of(2089,1,17,10,0));Booking two=createConfirmed(fixture,"queue-booking-2","queue-user-2","queue-vehicle-2","QJ-2",LocalDateTime.of(2089,1,17,10,0));
        try(var executor=Executors.newFixedThreadPool(2)){List<Future<QueueEntry>> results=executor.invokeAll(List.of(()->queues.createQueueEntry(ADMIN,"queue-entry-1",one.getBookingId(),fixture.service.getServiceId()),()->queues.createQueueEntry(ADMIN,"queue-entry-2",two.getBookingId(),fixture.service.getServiceId())));for(Future<QueueEntry> result:results)assertNotNull(result.get());}
        assertEquals(List.of(1,2),queues.findAll(fixture.branchId).stream().map(QueueEntry::getPosition).toList());
    }

    @Test void queueDeletionAndBookingCancellationDetachQueueReferencesAtomically(){
        Fixture fixture=fixture("queue-detach",2);
        Booking removable=createConfirmed(fixture,"queue-delete-booking","queue-delete-user","queue-delete-vehicle","QD-1",LocalDateTime.of(2089,1,17,10,0));
        QueueEntry first=queues.createQueueEntry(ADMIN,"queue-delete-entry",removable.getBookingId(),fixture.service.getServiceId());
        Booking cancellable=createConfirmed(fixture,"queue-cancel-booking","queue-cancel-user","queue-cancel-vehicle","QD-2",LocalDateTime.of(2089,1,17,11,0));
        QueueEntry second=queues.createQueueEntry(ADMIN,"queue-cancel-entry",cancellable.getBookingId(),fixture.service.getServiceId());
        assertTrue(second.getEstimatedWaitMin()>first.getEstimatedWaitMin());

        queues.deleteQueueEntry(ADMIN,"queue-delete-entry");
        assertTrue(queueRepository.findById("queue-delete-entry").isEmpty());
        assertNull(bookingRepository.findById(removable.getBookingId()).orElseThrow().getQueueEntry());
        assertEquals(first.getEstimatedWaitMin(),queues.findById("queue-cancel-entry").getEstimatedWaitMin());

        assertEquals(BookingStatus.CANCELLED,bookings.cancelBooking(ADMIN,cancellable.getBookingId()).getStatus());
        assertTrue(queueRepository.findById("queue-cancel-entry").isEmpty());
        assertNull(bookingRepository.findById(cancellable.getBookingId()).orElseThrow().getQueueEntry());
    }

    @Test void queueRollbackPreservesOriginalOrdering(){
        Fixture fixture=fixture("queue-rollback",2);Booking one=createConfirmed(fixture,"rollback-booking-1","rollback-user-1","rollback-vehicle-1","QR-1",LocalDateTime.of(2089,1,17,10,0));Booking two=createConfirmed(fixture,"rollback-booking-2","rollback-user-2","rollback-vehicle-2","QR-2",LocalDateTime.of(2089,1,17,10,0));queues.createQueueEntry(ADMIN,"rollback-entry-1",one.getBookingId(),fixture.service.getServiceId());queues.createQueueEntry(ADMIN,"rollback-entry-2",two.getBookingId(),fixture.service.getServiceId());List<String> original=queues.findAll(fixture.branchId).stream().map(QueueEntry::getQueueEntryId).toList();
        assertThrows(IllegalStateException.class,()->transactions.write(()->{queues.updatePosition(ADMIN,"rollback-entry-2",1);throw new IllegalStateException("injected");}));
        assertEquals(original,queues.findAll(fixture.branchId).stream().map(QueueEntry::getQueueEntryId).toList());
    }

    @Test void concurrentQueueMutationsRemainIsolatedByBranch() throws Exception {
        Fixture first=fixture("branch-one",1);Fixture second=fixture("branch-two",1);Booking one=createConfirmed(first,"branch-one-booking","branch-one-user-2","branch-one-vehicle-2","BI-1",LocalDateTime.of(2089,1,17,10,0));Booking two=createConfirmed(second,"branch-two-booking","branch-two-user-2","branch-two-vehicle-2","BI-2",LocalDateTime.of(2089,1,17,10,0));
        try(var executor=Executors.newFixedThreadPool(2)){List<Future<QueueEntry>> results=executor.invokeAll(List.of(()->queues.createQueueEntry(ADMIN,"branch-one-entry",one.getBookingId(),first.service.getServiceId()),()->queues.createQueueEntry(ADMIN,"branch-two-entry",two.getBookingId(),second.service.getServiceId())));for(Future<QueueEntry> result:results)assertEquals(1,result.get().getPosition());}
        assertEquals(1,queues.findAll(first.branchId).size());assertEquals(1,queues.findAll(second.branchId).size());
    }

    @Test void concurrentCallNextSelectsTheSingleWaitingEntryOnlyOnce() throws Exception {
        Fixture fixture=fixture("call-next",1);Booking booking=createConfirmed(fixture,"call-booking","call-user","call-vehicle","CALL-1",LocalDateTime.of(2089,1,17,10,0));queues.createQueueEntry(ADMIN,"call-entry",booking.getBookingId(),fixture.service.getServiceId());
        try(var executor=Executors.newFixedThreadPool(2)){List<Future<QueueEntry>> results=executor.invokeAll(List.of(()->queues.callNext(ADMIN,fixture.branchId),()->queues.callNext(ADMIN,fixture.branchId)));int success=0;int absent=0;for(Future<QueueEntry> result:results){try{result.get();success++;}catch(Exception failure){if(failure.getCause() instanceof ResourceNotFoundException)absent++;}}assertEquals(1,success);assertEquals(1,absent);}
    }

    @Test void optimisticVersionsRejectLostUpdatesAcrossIndependentTransactions() throws Exception {
        createUser("optimistic-user","optimistic@example.test");CyclicBarrier barrier=new CyclicBarrier(2);
        Callable<Boolean> update=()->{try{transactions.write(()->{User user=userRepository.findById("optimistic-user").orElseThrow();try{barrier.await();}catch(Exception failure){throw new IllegalStateException(failure);}user.setPhone(Thread.currentThread().getName());userRepository.update(user);return null;});return true;}catch(RuntimeException failure){return false;}};
        try(var executor=Executors.newFixedThreadPool(2)){List<Future<Boolean>> results=executor.invokeAll(List.of(update,update));assertEquals(1,results.stream().filter(result->{try{return result.get();}catch(Exception failure){return false;}}).count());}
    }

    @Test void timezoneAndDstOverlapDecisionsSurvivePostgresRoundTrips(){
        User user=createUser("dst-user","dst-user@example.test");Vehicle vehicle=vehicles.createVehicle(ADMIN,user.getUserId(),"dst-vehicle","DST-1","SUV","A","B","C",null);marketplace.registerBusiness(ADMIN,new RegisterBusinessCommand("dst-business","DST Business","dst-business@example.test","0123456789","dst-registration"));marketplace.createBranch(ADMIN,"dst-business",new CreateBranchCommand("dst-branch","New York","1 Main",null,"New York","New York","10001","US",new BigDecimal("40.7128"),new BigDecimal("-74.0060"),"America/New_York",true));scheduling.replaceOperatingSchedule(ADMIN,"dst-branch",new ReplaceOperatingScheduleCommand(List.of(new WeeklyOperatingIntervalCommand(DayOfWeek.SUNDAY,LocalTime.MIDNIGHT,LocalTime.of(4,0)))));Service service=catalog.createService("dst-service","DST Wash","test",new BigDecimal("100.00"),30);offerings.createOffering(ADMIN,"dst-branch",new CreateServiceOfferingCommand("dst-offering",service.getServiceId(),new BigDecimal("100.00"),30,2));
        assertEquals("America/New_York",marketplace.findBranch("dst-branch").timezone());BranchAvailabilitySearchService availability=applicationContext().getBean(BranchAvailabilitySearchService.class);
        assertTrue(availability.findAvailableBranches(new BranchAvailabilitySearchCriteria(service.getServiceId(),OffsetDateTime.parse("2090-11-05T01:30:00-04:00").toInstant(),null,null,null)).isEmpty());
        assertTrue(availability.findAvailableBranches(new BranchAvailabilitySearchCriteria(service.getServiceId(),OffsetDateTime.parse("2090-11-05T01:30:00-05:00").toInstant(),null,null,null)).isEmpty());
        assertFalse(availability.findAvailableBranches(new BranchAvailabilitySearchCriteria(service.getServiceId(),OffsetDateTime.parse("2090-11-05T00:30:00-04:00").toInstant(),null,null,null)).isEmpty());
        assertThrows(BusinessRuleViolationException.class,()->bookings.createBooking(ADMIN,"dst-ambiguous",user.getUserId(),vehicle.getVehicleId(),"dst-branch","dst-offering",LocalDateTime.of(2090,11,5,1,30),null));
    }

    @Test void dataAuthenticationAndOperationalReadsSurviveApplicationRestart(){
        String prefix="restart";
        try(ConfigurableApplicationContext first=startContext()){
            Fixture fixture=fixture(first,prefix,2);Booking booking=createConfirmed(first,fixture,"restart-booking","restart-user",fixture.vehicle,"restart-vehicle",LocalDateTime.of(2089,1,17,10,0));first.getBean(QueueManagementService.class).createQueueEntry(ADMIN,"restart-queue",booking.getBookingId(),fixture.service.getServiceId());
        }
        try(ConfigurableApplicationContext second=startContext()){
            var authentication=second.getBean(UserAuthenticationService.class).authenticate("restart@example.test",PASSWORD);
            assertNotNull(authentication.accessToken());assertEquals("CUSTOMER",authentication.user().getRole().getRoleName());
            assertEquals("restart-branch",second.getBean(MarketplaceManagementService.class).findBranch("restart-branch").branchId());
            assertEquals("restart-booking",second.getBean(BookingManagementService.class).findById("restart-booking").getBookingId());
            assertEquals("restart-queue",second.getBean(QueueManagementService.class).findById("restart-queue").getQueueEntryId());
            assertFalse(second.getBean(NotificationManagementService.class).findByUserId("restart-user").isEmpty());
            assertFalse(second.getBean(BranchAvailabilitySearchService.class).findAvailableBranches(new BranchAvailabilitySearchCriteria("restart-service",Instant.parse("2089-01-17T11:00:00Z"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("10"))).isEmpty());
            var recommendations=second.getBean(RecommendationService.class).recommend(new RecommendationSearchCriteria(BigDecimal.ZERO,BigDecimal.ZERO,"restart-service",Instant.parse("2089-01-17T11:00:00Z"),RecommendationPreference.BEST_OVERALL,new BigDecimal("10")));
            assertFalse(recommendations.isEmpty());assertEquals("restart-branch",recommendations.getFirst().branchId());
        }
    }

    private Fixture fixture(String prefix,int capacity){return fixture(users,vehicles,marketplace,scheduling,catalog,offerings,prefix,capacity);}
    private static Fixture fixture(ConfigurableApplicationContext context,String prefix,int capacity){return fixture(context.getBean(UserManagementService.class),context.getBean(VehicleManagementService.class),context.getBean(MarketplaceManagementService.class),context.getBean(BranchSchedulingService.class),context.getBean(ServiceCatalogService.class),context.getBean(ServiceOfferingService.class),prefix,capacity);}
    private static Fixture fixture(UserManagementService users,VehicleManagementService vehicles,MarketplaceManagementService marketplace,BranchSchedulingService scheduling,ServiceCatalogService catalog,ServiceOfferingService offerings,String prefix,int capacity){User user=users.createUser(new CreateUserCommand(prefix+"-user",prefix+" User",prefix+"@example.test","0123456789",PASSWORD));Vehicle vehicle=vehicles.createVehicle(ADMIN,user.getUserId(),prefix+"-vehicle",prefix.toUpperCase()+"-1","SUV","A","B","C",null);marketplace.registerBusiness(ADMIN,new RegisterBusinessCommand(prefix+"-business",prefix+" Business",prefix+"-business@example.test","0123456789",prefix+"-registration"));String branch=prefix+"-branch";marketplace.createBranch(ADMIN,prefix+"-business",new CreateBranchCommand(branch,prefix+" Branch","1 Main",null,"Cape Town","Western Cape","8001","ZA",BigDecimal.ZERO,BigDecimal.ZERO,"UTC",true));scheduling.replaceOperatingSchedule(ADMIN,branch,new ReplaceOperatingScheduleCommand(Arrays.stream(DayOfWeek.values()).map(day->new WeeklyOperatingIntervalCommand(day,LocalTime.MIDNIGHT,LocalTime.of(23,59,59,999999999))).toList()));Service service=catalog.createService(prefix+"-service",prefix+" Wash","test",new BigDecimal("100.00"),30);String offering=prefix+"-offering";offerings.createOffering(ADMIN,branch,new CreateServiceOfferingCommand(offering,service.getServiceId(),new BigDecimal("100.00"),30,capacity));return new Fixture(user,vehicle,service,branch,offering);}
    private Booking createConfirmed(Fixture fixture,String bookingId,String userId,String vehicleId,String plate,LocalDateTime at){User user=createUser(userId,userId+"@example.test");Vehicle vehicle=vehicles.createVehicle(ADMIN,userId,vehicleId,plate,"SUV","A","B","C",null);return bookings.confirmBooking(ADMIN,bookings.createBooking(ADMIN,bookingId,userId,vehicle.getVehicleId(),fixture.branchId,fixture.offeringId,at,null).getBookingId());}
    private static Booking createConfirmed(ConfigurableApplicationContext context,Fixture fixture,String bookingId,String userId,Vehicle vehicle,String vehicleId,LocalDateTime at){BookingManagementService bookings=context.getBean(BookingManagementService.class);return bookings.confirmBooking(ADMIN,bookings.createBooking(ADMIN,bookingId,userId,vehicleId,fixture.branchId,fixture.offeringId,at,null).getBookingId());}
    private User createUser(String id,String email){return users.createUser(new CreateUserCommand(id,id+" Name",email,"0123456789",PASSWORD));}
    private void awaitAdvisoryLockWaiter(){
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline){
            Integer waiting=jdbc.queryForObject(
                    "select count(*) from pg_locks where locktype='advisory' and not granted",Integer.class);
            if(waiting!=null&&waiting>0)return;
            Thread.onSpinWait();
        }
        throw new AssertionError("Timed out waiting for the tenant mutation to block on its advisory lock");
    }
    private User createUserValue(String id,String email){return com.carwash.identity.domain.User.withEncodedPassword(id,id+" Name",email,"0123456789","encoded",com.carwash.identity.domain.RoleCatalog.role(com.carwash.identity.domain.RoleName.CUSTOMER));}
    private static Service service(String id){Service value=new Service();value.setServiceId(id);value.setServiceName("Missing");value.setDescription("missing");value.setPrice(BigDecimal.ONE);value.setEstimatedDurationMin(1);value.setActive(true);value.setCreatedAt(LocalDateTime.of(2089,1,1,0,0));return value;}
    @Autowired ConfigurableApplicationContext springContext;
    private ConfigurableApplicationContext applicationContext(){return springContext;}
    private ConfigurableApplicationContext startContext(){return new SpringApplicationBuilder(CarwashBookingQueueSystemApplication.class).profiles("test","postgres").web(WebApplicationType.SERVLET).run("--server.port=0","--spring.autoconfigure.exclude=","--spring.datasource.url="+POSTGRES.getJdbcUrl(),"--spring.datasource.username="+POSTGRES.getUsername(),"--spring.datasource.password="+POSTGRES.getPassword());}
    private record Fixture(User user,Vehicle vehicle,Service service,String branchId,String offeringId) { }
}
