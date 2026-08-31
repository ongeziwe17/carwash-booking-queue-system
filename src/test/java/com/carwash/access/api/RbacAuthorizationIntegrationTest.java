package com.carwash.access.api;

import com.carwash.access.infrastructure.JwtSecurityProperties;
import com.carwash.booking.domain.Booking;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.catalog.domain.Service;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.identity.domain.RoleName;
import com.carwash.identity.domain.TenantMembershipRepository;
import com.carwash.booking.application.BookingManagementService;
import com.carwash.queue.application.QueueManagementService;
import com.carwash.catalog.application.ServiceCatalogService;
import com.carwash.catalog.application.CreateServiceOfferingCommand;
import com.carwash.catalog.application.ServiceOfferingService;
import com.carwash.marketplace.application.CreateBranchCommand;
import com.carwash.marketplace.application.MarketplaceManagementService;
import com.carwash.marketplace.application.BranchSchedulingService;
import com.carwash.marketplace.application.ReplaceOperatingScheduleCommand;
import com.carwash.marketplace.application.WeeklyOperatingIntervalCommand;
import com.carwash.marketplace.application.RegisterBusinessCommand;
import com.carwash.identity.application.UserManagementService;
import com.carwash.identity.application.TenantMembershipManagementService;
import com.carwash.vehicle.application.VehicleManagementService;
import com.carwash.testsupport.ApiContractAssertions;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.TestDates;
import com.carwash.testsupport.UserFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RbacAuthorizationIntegrationTest extends ApiIntegrationTestSupport {

    private static final String PASSWORD = UserFixtureBuilder.DEFAULT_PASSWORD;

    @Autowired UserManagementService users;
    @Autowired TenantMembershipManagementService tenantMemberships;
    @Autowired TenantMembershipRepository tenantMembershipRepository;
    @Autowired VehicleManagementService vehicles;
    @Autowired ServiceCatalogService services;
    @Autowired ServiceOfferingService offerings;
    @Autowired MarketplaceManagementService marketplace;
    @Autowired BranchSchedulingService branchScheduling;
    @Autowired BookingManagementService bookings;
    @Autowired QueueManagementService queues;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired JwtSecurityProperties jwtProperties;
    @Autowired Clock securityClock;

    private int slotSequence;
    private String defaultBusinessId;
    private String defaultBranchId;

    @Test
    void customerCanAccessOwnResourcesButNotOtherCustomersOrOperationalApis() throws Exception {
        LoginIdentity customer = registerAndLogin(RoleName.CUSTOMER);
        LoginIdentity other = registerAndLogin(RoleName.CUSTOMER);
        ResourceSet ownResources = createQueuedResourceSet(customer.userId());
        ResourceSet otherResources = createQueuedResourceSet(other.userId());

        mockMvc.perform(get("/api/users/{id}", customer.userId()).header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(customer.userId()))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("encodedPassword"))));
        mockMvc.perform(get("/api/vehicles/{id}", ownResources.primaryVehicleId()).header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/{id}", ownResources.bookingId()).header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/queue-entries/{id}", ownResources.queueEntryId()).header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isOk());
        assertAvailabilityAllowed(customer, ownResources.serviceId());

        assertForbidden(mockMvc.perform(get("/api/users/{id}", other.userId()).header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertNotFound(mockMvc.perform(get("/api/vehicles/{id}", otherResources.primaryVehicleId()).header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertNotFound(mockMvc.perform(get("/api/bookings/{id}", otherResources.bookingId()).header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertNotFound(mockMvc.perform(post("/api/bookings/{id}/reschedule", otherResources.bookingId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(rescheduleRequest(nextScheduledTime()))));
        assertNotFound(mockMvc.perform(get("/api/queue-entries/{id}", otherResources.queueEntryId()).header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertForbidden(mockMvc.perform(post("/api/services").header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(serviceRequest()))));
        assertForbidden(mockMvc.perform(post("/api/queue-entries/call-next")
                .param("branchId", ownResources.branchId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertForbidden(mockMvc.perform(post("/api/queue-entries/{id}/call", ownResources.queueEntryId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertForbidden(mockMvc.perform(get("/api/reports/daily-summary").header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .param("date", TestDates.future().toLocalDate().toString())
                .param("branchId", ownResources.branchId())));
        assertForbidden(mockMvc.perform(put("/api/admin/users/{id}/role", other.userId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleName\":\"STAFF\"}")));
    }

    @Test
    void staffCanOperateVehiclesBookingsAndQueuesButCannotManageServicesReportsOrRoles() throws Exception {
        LoginIdentity staff = registerAndLogin(RoleName.STAFF);
        LoginIdentity customer = registerAndLogin(RoleName.CUSTOMER);
        LoginIdentity other = registerAndLogin(RoleName.CUSTOMER);
        ResourceSet resources = createResourceSetWithoutQueue(customer.userId());

        mockMvc.perform(get("/api/vehicles/{id}", resources.primaryVehicleId()).header(HttpHeaders.AUTHORIZATION, staff.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/{id}", resources.bookingId()).header(HttpHeaders.AUTHORIZATION, staff.bearer()))
                .andExpect(status().isOk());
        assertAvailabilityAllowed(staff, resources.serviceId());
        assertOperationalUpdateCannotTransferOwner(staff, resources, other.userId());
        enqueue(resources);
        mockMvc.perform(post("/api/queue-entries/call-next")
                        .param("branchId", resources.branchId())
                        .header(HttpHeaders.AUTHORIZATION, staff.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.queueStatus").value("CALLED"));
        assertForbidden(mockMvc.perform(post("/api/services").header(HttpHeaders.AUTHORIZATION, staff.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(serviceRequest()))));
        assertForbidden(mockMvc.perform(get("/api/reports/daily-summary").header(HttpHeaders.AUTHORIZATION, staff.bearer())
                .param("date", TestDates.future().toLocalDate().toString())
                .param("branchId", resources.branchId())));
        assertForbidden(mockMvc.perform(put("/api/admin/users/{id}/role", customer.userId())
                .header(HttpHeaders.AUTHORIZATION, staff.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleName\":\"BUSINESS_OWNER\"}")));
    }

    @Test
    void businessOwnerCanOperateAndReportButCannotManageGlobalServicesOrAssignRoles() throws Exception {
        LoginIdentity owner = registerAndLogin(RoleName.BUSINESS_OWNER);
        LoginIdentity customer = registerAndLogin(RoleName.CUSTOMER);
        LoginIdentity other = registerAndLogin(RoleName.CUSTOMER);
        ResourceSet resources = createResourceSetWithoutQueue(customer.userId());

        mockMvc.perform(get("/api/vehicles/{id}", resources.primaryVehicleId()).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/{id}", resources.bookingId()).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk());
        assertAvailabilityAllowed(owner, resources.serviceId());
        assertOperationalUpdateCannotTransferOwner(owner, resources, other.userId());
        enqueue(resources);
        mockMvc.perform(post("/api/queue-entries/{id}/call", resources.queueEntryId())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())).andExpect(status().isOk());
        mockMvc.perform(post("/api/services").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(serviceRequest())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/daily-summary").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .param("date", TestDates.future().toLocalDate().toString())
                        .param("branchId", resources.branchId())).andExpect(status().isOk());
        assertForbidden(mockMvc.perform(put("/api/admin/users/{id}/role", customer.userId())
                .header(HttpHeaders.AUTHORIZATION, owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleName\":\"STAFF\"}")));
    }

    @Test
    void platformAdministratorCanManageUsersRolesAndOperationalApisButCannotRemoveLastAdministrator() throws Exception {
        LoginIdentity administrator = registerAndLogin(RoleName.PLATFORM_ADMIN);
        LoginIdentity customer = registerAndLogin(RoleName.CUSTOMER);
        LoginIdentity other = registerAndLogin(RoleName.CUSTOMER);
        ResourceSet resources = createResourceSetWithoutQueue(customer.userId());

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, administrator.bearer())).andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/users/{id}/role", customer.userId())
                        .header(HttpHeaders.AUTHORIZATION, administrator.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleName", "STAFF", "businessId", defaultBusinessId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roleName").value("STAFF"));
        mockMvc.perform(get("/api/vehicles").param("businessId", defaultBusinessId)
                .header(HttpHeaders.AUTHORIZATION, administrator.bearer())).andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings").param("businessId", defaultBusinessId)
                .header(HttpHeaders.AUTHORIZATION, administrator.bearer())).andExpect(status().isOk());
        mockMvc.perform(get("/api/queue-entries").param("businessId", defaultBusinessId)
                .header(HttpHeaders.AUTHORIZATION, administrator.bearer())).andExpect(status().isOk());
        assertAvailabilityAllowed(administrator, resources.serviceId());
        mockMvc.perform(post("/api/services").header(HttpHeaders.AUTHORIZATION, administrator.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(serviceRequest())))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/reports/daily-summary").header(HttpHeaders.AUTHORIZATION, administrator.bearer())
                        .param("date", TestDates.future().toLocalDate().toString())
                        .param("branchId", resources.branchId())).andExpect(status().isOk());
        assertOperationalUpdateCannotTransferOwner(administrator, resources, other.userId());

        mockMvc.perform(delete("/api/users/{id}", administrator.userId()).header(HttpHeaders.AUTHORIZATION, administrator.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The last active platform administrator cannot be deleted"));
        mockMvc.perform(put("/api/admin/users/{id}/role", administrator.userId())
                        .header(HttpHeaders.AUTHORIZATION, administrator.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"CUSTOMER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The last active platform administrator cannot be demoted"));
    }

    @Test
    void customerBookingUpdatesPreserveOwnerAndRejectAnotherCustomersVehicle() throws Exception {
        LoginIdentity customer = registerAndLogin(RoleName.CUSTOMER);
        LoginIdentity other = registerAndLogin(RoleName.CUSTOMER);
        ResourceSet resources = createResourceSetWithoutQueue(customer.userId());
        ResourceSet otherResources = createResourceSetWithoutQueue(other.userId());

        Map<String, Object> allowedUpdate = updateBookingRequest(
                resources.alternateVehicleId(), resources.serviceOfferingId());
        mockMvc.perform(put("/api/bookings/{id}", resources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allowedUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userId").value(customer.userId()))
                .andExpect(jsonPath("$.vehicle.vehicleId").value(resources.alternateVehicleId()));
        assertEquals(customer.userId(), bookings.findById(resources.bookingId()).getUser().getUserId());

        LocalDateTime rescheduled = nextScheduledTime();
        mockMvc.perform(post("/api/bookings/{id}/reschedule", resources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleRequest(rescheduled)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDateTime").value(apiDateTime(rescheduled)))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Map<String, Object> rejectedUpdate = updateBookingRequest(
                otherResources.primaryVehicleId(), resources.serviceOfferingId());
        mockMvc.perform(put("/api/bookings/{id}", resources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectedUpdate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vehicle does not belong to booking owner"));
        assertEquals(customer.userId(), bookings.findById(resources.bookingId()).getUser().getUserId());
        assertEquals(resources.alternateVehicleId(), bookings.findById(resources.bookingId()).getVehicle().getVehicleId());
    }

    @Test
    void roleChangesInvalidateOldTokensAndNewLoginUsesCurrentCatalogueAuthorities() throws Exception {
        LoginIdentity customer = registerAndLogin(RoleName.CUSTOMER);
        tenantMemberships.assignRole(customer.userId(), RoleName.STAFF, ensureBusinessId());
        assertUnauthorized(mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        String newToken = authentication.login(customer.email(), PASSWORD);
        mockMvc.perform(get("/api/vehicles").header(HttpHeaders.AUTHORIZATION, bearer(newToken))).andExpect(status().isOk());
    }

    @Test
    void missingForgedAndReassignedTenantClaimsInvalidateOperationalTokens() throws Exception {
        LoginIdentity staff = registerAndLogin(RoleName.STAFF);
        String tenantA = ensureBusinessId();
        String tenantB = ids.business();
        marketplace.registerBusiness(new RegisterBusinessCommand(
                tenantB, "Second RBAC Wash", ids.emailFor(tenantB), "+27821234568", null));

        String missingClaim = signedToken(staff.userId(), RoleName.STAFF.name(), null, List.of());
        assertUnauthorized(mockMvc.perform(get("/api/bookings").header(
                HttpHeaders.AUTHORIZATION, bearer(missingClaim))));

        String forgedClaim = signedToken(staff.userId(), RoleName.STAFF.name(), tenantB, List.of());
        assertUnauthorized(mockMvc.perform(get("/api/bookings").header(
                HttpHeaders.AUTHORIZATION, bearer(forgedClaim))));

        tenantMemberships.assignOrReplace(staff.userId(), tenantB);
        assertUnauthorized(mockMvc.perform(get("/api/bookings").header(
                HttpHeaders.AUTHORIZATION, staff.bearer())));

        String reassigned = authentication.login(staff.email(), PASSWORD);
        mockMvc.perform(get("/api/bookings").header(HttpHeaders.AUTHORIZATION, bearer(reassigned)))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertNotEquals(tenantA, tenantB);
    }

    @Test
    void legacyOperationalUserWithoutMembershipCannotLogin() throws Exception {
        LoginIdentity staff = registerAndLogin(RoleName.STAFF);
        assertTrue(tenantMembershipRepository.deleteById(staff.userId()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", staff.email(),
                                "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        assertUnauthorized(mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, staff.bearer())));
    }

    @Test
    void injectedPermissionClaimCannotGrantAdditionalAccess() throws Exception {
        LoginIdentity customer = registerAndLogin(RoleName.CUSTOMER);
        String injectedToken = signedTokenWithInjectedPermissions(customer.userId(), RoleName.CUSTOMER.name(),
                List.of("ROLE_ASSIGN", "SERVICE_MANAGE", "USER_ADMIN"));
        assertForbidden(mockMvc.perform(post("/api/services").header(HttpHeaders.AUTHORIZATION, bearer(injectedToken))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(serviceRequest()))));
        assertForbidden(mockMvc.perform(put("/api/admin/users/{id}/role", customer.userId())
                .header(HttpHeaders.AUTHORIZATION, bearer(injectedToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleName\":\"PLATFORM_ADMIN\"}")));
    }

    @Test
    void unknownRoleClaimIsRejectedAsUnauthorized() throws Exception {
        LoginIdentity customer = registerAndLogin(RoleName.CUSTOMER);
        String token = signedTokenWithInjectedPermissions(customer.userId(), "SUPER_ADMIN", List.of("USER_ADMIN", "ROLE_ASSIGN"));
        assertUnauthorized(mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token))));
    }

    @Test
    void authenticationAndAuthorizationErrorsUseSafeJsonContracts() throws Exception {
        assertUnauthorized(mockMvc.perform(get("/api/services")));
        assertUnauthorized(mockMvc.perform(get("/api/availability")
                .param("serviceId", "service").param("date", TestDates.future().toLocalDate().toString())));
        assertUnauthorized(mockMvc.perform(post("/api/bookings/missing/reschedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rescheduleRequest(nextScheduledTime()))));
        LoginIdentity customer = registerAndLogin(RoleName.CUSTOMER);
        assertForbidden(mockMvc.perform(post("/api/services").header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(serviceRequest()))));
    }

    private void assertOperationalUpdateCannotTransferOwner(LoginIdentity operator, ResourceSet resources, String attemptedOwnerId)
            throws Exception {
        Map<String, Object> allowed = updateBookingRequest(
                resources.alternateVehicleId(), resources.serviceOfferingId());
        mockMvc.perform(put("/api/bookings/{id}", resources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, operator.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allowed)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.userId").value(resources.ownerId()));
        LocalDateTime rescheduled = nextScheduledTime();
        mockMvc.perform(post("/api/bookings/{id}/reschedule", resources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, operator.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleRequest(rescheduled)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDateTime").value(apiDateTime(rescheduled)))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        Map<String, Object> transfer = new LinkedHashMap<>(allowed);
        transfer.put("userId", attemptedOwnerId);
        mockMvc.perform(put("/api/bookings/{id}", resources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, operator.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertEquals(resources.ownerId(), bookings.findById(resources.bookingId()).getUser().getUserId());
    }

    private LoginIdentity registerAndLogin(RoleName roleName) throws Exception {
        String userId = ids.user();
        String email = ids.emailFor(userId);
        api.createUser(UserFixtureBuilder.valid(ids).userId(userId).email(email).build())
                .andExpect(status().isCreated());
        if (roleName == RoleName.STAFF || roleName == RoleName.BUSINESS_OWNER) {
            tenantMemberships.assignRole(userId, roleName, ensureBusinessId());
        } else if (roleName != RoleName.CUSTOMER) {
            users.assignRole(userId, roleName);
        }
        return new LoginIdentity(userId, email, authentication.login(email, PASSWORD));
    }

    private ResourceSet createQueuedResourceSet(String ownerId) {
        ResourceSet resources = createResourceSetWithoutQueue(ownerId);
        enqueue(resources);
        return resources;
    }

    private ResourceSet createResourceSetWithoutQueue(String ownerId) {
        String primaryVehicleId = ids.vehicle();
        String alternateVehicleId = ids.vehicle();
        String serviceId = ids.service();
        String bookingId = ids.booking();
        String queueEntryId = ids.queueEntry();
        Vehicle primary = vehicles.createVehicle(new Vehicle(primaryVehicleId, ids.plate(), "SUV", "Toyota", "Rav4", "Black", ""), ownerId);
        vehicles.createVehicle(new Vehicle(alternateVehicleId, ids.plate(), "Sedan", "Honda", "Civic", "White", ""), ownerId);
        Service service = services.createService(new Service(serviceId, "RBAC Wash", "authorization fixture", BigDecimal.valueOf(200), 30));
        String branchId = ensureBranch();
        String offeringId = ids.offering();
        offerings.createOffering(branchId, new CreateServiceOfferingCommand(
                offeringId, serviceId, BigDecimal.valueOf(200), 30, 2));
        Booking booking = bookings.createBooking(new Booking(
                bookingId, users.findById(ownerId), primary, branchId, offeringId, service,
                nextScheduledTime(), "authorization fixture"));
        bookings.confirmBooking(bookingId);
        return new ResourceSet(
                ownerId, primaryVehicleId, alternateVehicleId, serviceId, branchId, offeringId,
                bookingId, queueEntryId);
    }

    private void enqueue(ResourceSet resources) {
        Booking booking = bookings.findById(resources.bookingId());
        queues.createQueueEntry(new QueueEntry(resources.queueEntryId(), booking, booking.getService()));
    }

    private Map<String, Object> updateBookingRequest(String vehicleId, String serviceOfferingId) {
        return Map.of("vehicleId", vehicleId, "serviceOfferingId", serviceOfferingId,
                "specialRequest", "authorization update");
    }

    private String ensureBranch() {
        if (defaultBranchId != null) return defaultBranchId;
        String businessId = ids.business();
        defaultBusinessId = businessId;
        marketplace.registerBusiness(new RegisterBusinessCommand(
                businessId, "RBAC Wash", ids.emailFor(businessId), "+27821234567", null));
        defaultBranchId = ids.branch();
        marketplace.createBranch(businessId, new CreateBranchCommand(
                defaultBranchId, "RBAC Branch", "1 Test Street", null, "Cape Town", "Western Cape",
                "8001", "ZA", new BigDecimal("-33.9249"), new BigDecimal("18.4241"),
                "Africa/Johannesburg", true));
        branchScheduling.replaceOperatingSchedule(defaultBranchId, new ReplaceOperatingScheduleCommand(
                java.util.Arrays.stream(DayOfWeek.values())
                        .map(day -> new WeeklyOperatingIntervalCommand(
                                day, LocalTime.of(8, 0), LocalTime.of(17, 0)))
                        .toList()));
        return defaultBranchId;
    }

    private String ensureBusinessId() {
        ensureBranch();
        return defaultBusinessId;
    }

    private void assertAvailabilityAllowed(LoginIdentity identity, String serviceId) throws Exception {
        mockMvc.perform(get("/api/availability")
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                        .param("serviceId", serviceId)
                        .param("date", TestDates.future().toLocalDate().toString()))
                .andExpect(status().isOk());
    }

    private String rescheduleRequest(LocalDateTime scheduledDateTime) throws Exception {
        return objectMapper.writeValueAsString(Map.of("scheduledDateTime", scheduledDateTime.toString()));
    }

    private String apiDateTime(LocalDateTime value) {
        return value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    private Map<String, Object> serviceRequest() {
        return Map.of("serviceId", ids.service(), "serviceName", "Managed Service",
                "description", "RBAC service management test", "price", BigDecimal.valueOf(120), "estimatedDurationMin", 25);
    }

    private LocalDateTime nextScheduledTime() {
        return TestDates.futureDays(60 + ++slotSequence);
    }

    private void assertUnauthorized(ResultActions action) throws Exception {
        action.andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
        ApiContractAssertions.assertNoSensitiveData(action, false);
    }

    private void assertForbidden(ResultActions action) throws Exception {
        action.andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));
        ApiContractAssertions.assertNoSensitiveData(action, false);
    }

    private void assertNotFound(ResultActions action) throws Exception {
        action.andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));
        ApiContractAssertions.assertNoSensitiveData(action, false);
    }

    private String signedTokenWithInjectedPermissions(String subject, String roleClaim, List<String> injectedPermissions) {
        return signedToken(subject, roleClaim, null, injectedPermissions);
    }

    private String signedToken(
            String subject,
            String roleClaim,
            String tenantId,
            List<String> injectedPermissions
    ) {
        Instant issuedAt = securityClock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(jwtProperties.issuer()).subject(subject)
                .claim("role", roleClaim).claim("permissions", injectedPermissions).issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(jwtProperties.accessTokenTtl())).id(subject + "-test-token");
        if (tenantId != null) claims.claim("tenant_id", tenantId);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private String bearer(String token) { return "Bearer " + token; }
    private record LoginIdentity(String userId, String email, String token) { String bearer() { return "Bearer " + token; } }
    private record ResourceSet(String ownerId, String primaryVehicleId, String alternateVehicleId,
                               String serviceId, String branchId, String serviceOfferingId,
                               String bookingId, String queueEntryId) {}
}
