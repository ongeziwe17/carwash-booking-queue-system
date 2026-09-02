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
@ActiveProfiles(profiles={"test","postgres"},inheritProfiles=false)
@Testcontainers
class PostgresPersistenceIntegrationTest {
    private static final String PASSWORD="StrongPassword!123";
    private static final TenantAccessContext ADMIN =
            new TenantAccessContext("test-platform-admin", RoleName.PLATFORM_ADMIN, null);
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @DynamicPropertySource static void postgres(DynamicPropertyRegistry properties){
        properties.add("spring.autoconfigure.exclude",()->"");
        properties.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username",POSTGRES::getUsername);
        properties.add("spring.datasource.password",POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
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
        assertEquals("5",flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(5,jdbc.queryForObject("select count(*) from flyway_schema_history where success",Integer.class));
        assertEquals(4,jdbc.queryForObject("select count(*) from roles",Integer.class));
    }

    @Test void anEarlierMigrationUpgradesIncrementallyWithoutRecreatingData(){
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("create schema "+schema);
        try{
            Flyway first=Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("3").cleanDisabled(true).load();
            assertEquals(3,first.migrate().migrationsExecuted);
            try(var connection=POSTGRES.createConnection("");var statement=connection.createStatement()){
                statement.execute("insert into "+schema+".businesses(business_id,business_name,contact_email,contact_phone,business_status,registered_at,updated_at) values ('upgrade-business','Upgrade','upgrade@example.test','0123456789','ACTIVE',timestamp '2089-01-01',timestamp '2089-01-01')");
                statement.execute("insert into "+schema+".users(user_id,full_name,email,phone,account_status,created_at) values ('legacy-unassigned-staff','Legacy Staff','legacy-staff@example.test','0123456789','ACTIVE',timestamp '2089-01-01')");
                statement.execute("insert into "+schema+".user_credentials(user_id,encoded_password) values ('legacy-unassigned-staff','encoded')");
                statement.execute("insert into "+schema+".user_role_assignments(user_id,role_id) values ('legacy-unassigned-staff','builtin:STAFF')");
            }
            Flyway latest=Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").cleanDisabled(true).load();
            assertEquals(2,latest.migrate().migrationsExecuted);
            assertTrue(latest.validateWithResult().validationSuccessful);
            assertEquals(1,jdbc.queryForObject("select count(*) from "+schema+".businesses where business_id='upgrade-business'",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from "+schema+".tenant_memberships where user_id='legacy-unassigned-staff'",Integer.class));
            assertEquals(1,jdbc.queryForObject("select count(*) from pg_indexes where schemaname='"+schema+"' and indexname='ix_bookings_branch_status_time_tenant'",Integer.class));
            assertEquals(4,jdbc.queryForObject("select count(*) from pg_indexes where schemaname='"+schema+"' and indexname like 'ix_audit_%_date'",Integer.class));
            assertEquals(2,jdbc.queryForObject("select count(*) from "+schema+".role_permissions where permission='AUDIT_READ'",Integer.class));
        }catch(Exception failure){throw new AssertionError(failure);}finally{jdbc.execute("drop schema "+schema+" cascade");}
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
            List<Future<Void>> futures=java.util.stream.IntStream.range(0,count).mapToObj(index->executor.submit(()->{
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
