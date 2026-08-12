package com.carwash.access.api;

import com.carwash.access.infrastructure.JwtSecurityProperties;
import com.carwash.booking.domain.Booking;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.catalog.domain.Service;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.identity.domain.RoleName;
import com.carwash.booking.application.BookingManagementService;
import com.carwash.queue.application.QueueManagementService;
import com.carwash.catalog.application.ServiceCatalogService;
import com.carwash.identity.application.UserManagementService;
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
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @Autowired VehicleManagementService vehicles;
    @Autowired ServiceCatalogService services;
    @Autowired BookingManagementService bookings;
    @Autowired QueueManagementService queues;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired JwtSecurityProperties jwtProperties;
    @Autowired Clock securityClock;

    private int slotSequence;

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
        assertForbidden(mockMvc.perform(get("/api/vehicles/{id}", otherResources.primaryVehicleId()).header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertForbidden(mockMvc.perform(get("/api/bookings/{id}", otherResources.bookingId()).header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertForbidden(mockMvc.perform(post("/api/bookings/{id}/reschedule", otherResources.bookingId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(rescheduleRequest(nextScheduledTime()))));
        assertForbidden(mockMvc.perform(get("/api/queue-entries/{id}", otherResources.queueEntryId()).header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertForbidden(mockMvc.perform(post("/api/services").header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(serviceRequest()))));
        assertForbidden(mockMvc.perform(post("/api/queue-entries/call-next")
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertForbidden(mockMvc.perform(post("/api/queue-entries/{id}/call", ownResources.queueEntryId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertForbidden(mockMvc.perform(get("/api/reports/daily-summary").header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .param("date", TestDates.future().toLocalDate().toString())));
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
                        .header(HttpHeaders.AUTHORIZATION, staff.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.queueStatus").value("CALLED"));
        assertForbidden(mockMvc.perform(post("/api/services").header(HttpHeaders.AUTHORIZATION, staff.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(serviceRequest()))));
        assertForbidden(mockMvc.perform(get("/api/reports/daily-summary").header(HttpHeaders.AUTHORIZATION, staff.bearer())
                .param("date", TestDates.future().toLocalDate().toString())));
        assertForbidden(mockMvc.perform(put("/api/admin/users/{id}/role", customer.userId())
                .header(HttpHeaders.AUTHORIZATION, staff.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleName\":\"BUSINESS_OWNER\"}")));
    }

    @Test
    void businessOwnerCanOperateAndManageServicesAndReportsButCannotAssignRoles() throws Exception {
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
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/reports/daily-summary").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .param("date", TestDates.future().toLocalDate().toString())).andExpect(status().isOk());
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
                        .content("{\"roleName\":\"STAFF\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roleName").value("STAFF"));
        mockMvc.perform(get("/api/vehicles").header(HttpHeaders.AUTHORIZATION, administrator.bearer())).andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings").header(HttpHeaders.AUTHORIZATION, administrator.bearer())).andExpect(status().isOk());
        mockMvc.perform(get("/api/queue-entries").header(HttpHeaders.AUTHORIZATION, administrator.bearer())).andExpect(status().isOk());
        assertAvailabilityAllowed(administrator, resources.serviceId());
        mockMvc.perform(post("/api/services").header(HttpHeaders.AUTHORIZATION, administrator.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(serviceRequest())))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/reports/daily-summary").header(HttpHeaders.AUTHORIZATION, administrator.bearer())
                        .param("date", TestDates.future().toLocalDate().toString())).andExpect(status().isOk());
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

        Map<String, Object> allowedUpdate = updateBookingRequest(resources.alternateVehicleId(), resources.serviceId());
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

        Map<String, Object> rejectedUpdate = updateBookingRequest(otherResources.primaryVehicleId(), resources.serviceId());
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
        users.assignRole(customer.userId(), RoleName.STAFF);
        assertUnauthorized(mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        String newToken = authentication.login(customer.email(), PASSWORD);
        mockMvc.perform(get("/api/vehicles").header(HttpHeaders.AUTHORIZATION, bearer(newToken))).andExpect(status().isOk());
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
        Map<String, Object> allowed = updateBookingRequest(resources.alternateVehicleId(), resources.serviceId());
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
        if (roleName != RoleName.CUSTOMER) {
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
        Booking booking = bookings.createBooking(new Booking(bookingId, users.findById(ownerId), primary, service, nextScheduledTime(), "authorization fixture"));
        bookings.confirmBooking(bookingId);
        return new ResourceSet(ownerId, primaryVehicleId, alternateVehicleId, serviceId, bookingId, queueEntryId);
    }

    private void enqueue(ResourceSet resources) {
        Booking booking = bookings.findById(resources.bookingId());
        queues.createQueueEntry(new QueueEntry(resources.queueEntryId(), booking, booking.getService()));
    }

    private Map<String, Object> updateBookingRequest(String vehicleId, String serviceId) {
        return Map.of("vehicleId", vehicleId, "serviceId", serviceId,
                "specialRequest", "authorization update");
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

    private String signedTokenWithInjectedPermissions(String subject, String roleClaim, List<String> injectedPermissions) {
        Instant issuedAt = securityClock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(jwtProperties.issuer()).subject(subject)
                .claim("role", roleClaim).claim("permissions", injectedPermissions).issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(jwtProperties.accessTokenTtl())).id(subject + "-test-token").build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String bearer(String token) { return "Bearer " + token; }
    private record LoginIdentity(String userId, String email, String token) { String bearer() { return "Bearer " + token; } }
    private record ResourceSet(String ownerId, String primaryVehicleId, String alternateVehicleId,
                               String serviceId, String bookingId, String queueEntryId) {}
}
