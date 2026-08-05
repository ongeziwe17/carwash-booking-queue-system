package com.carwash.api;

import com.carwash.domain.Booking;
import com.carwash.domain.QueueEntry;
import com.carwash.domain.Service;
import com.carwash.domain.Vehicle;
import com.carwash.config.JwtSecurityProperties;
import com.carwash.security.RoleName;
import com.carwash.service.BookingManagementService;
import com.carwash.service.QueueManagementService;
import com.carwash.service.ServiceCatalogService;
import com.carwash.service.UserManagementService;
import com.carwash.service.VehicleManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RbacAuthorizationIntegrationTest {

    private static final String PASSWORD = "LocalTestPassword123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserManagementService users;

    @Autowired
    VehicleManagementService vehicles;

    @Autowired
    ServiceCatalogService services;

    @Autowired
    BookingManagementService bookings;

    @Autowired
    QueueManagementService queues;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JwtSecurityProperties jwtProperties;

    @Autowired
    Clock securityClock;

    private final AtomicInteger slotSequence = new AtomicInteger();

    @Test
    void customerCanAccessOwnResourcesButNotOtherCustomersOrOperationalApis() throws Exception {
        LoginIdentity customer = registerAndLogin("customer", RoleName.CUSTOMER);
        LoginIdentity other = registerAndLogin("other-customer", RoleName.CUSTOMER);
        ResourceSet ownResources = createResourceSet(customer.userId(), "customer-own");
        ResourceSet otherResources = createResourceSet(other.userId(), "customer-other");

        mockMvc.perform(get("/api/users/{id}", customer.userId()).header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(customer.userId()))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("encodedPassword"))));

        mockMvc.perform(get("/api/vehicles/{id}", ownResources.primaryVehicleId())
                        .header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/{id}", ownResources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/queue-entries/{id}", ownResources.queueEntryId())
                        .header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isOk());

        assertForbidden(mockMvc.perform(get("/api/users/{id}", other.userId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertForbidden(mockMvc.perform(get("/api/vehicles/{id}", otherResources.primaryVehicleId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertForbidden(mockMvc.perform(get("/api/bookings/{id}", otherResources.bookingId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())));
        assertForbidden(mockMvc.perform(get("/api/queue-entries/{id}", otherResources.queueEntryId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())));

        assertForbidden(mockMvc.perform(post("/api/services")
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(serviceRequest("customer-denied-service")))));

        assertForbidden(mockMvc.perform(post("/api/queue-entries/{id}/call-next", ownResources.queueEntryId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())));

        assertForbidden(mockMvc.perform(get("/api/reports/daily-summary")
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .param("date", LocalDate.now().toString())));

        assertForbidden(mockMvc.perform(put("/api/admin/users/{id}/role", other.userId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleName\":\"STAFF\"}")));
    }

    @Test
    void staffCanOperateVehiclesBookingsAndQueuesButCannotManageServicesReportsOrRoles() throws Exception {
        LoginIdentity staff = registerAndLogin("staff", RoleName.STAFF);
        LoginIdentity customer = registerAndLogin("staff-customer", RoleName.CUSTOMER);
        LoginIdentity other = registerAndLogin("staff-other", RoleName.CUSTOMER);
        ResourceSet resources = createResourceSet(customer.userId(), "staff-resources");

        mockMvc.perform(get("/api/vehicles/{id}", resources.primaryVehicleId())
                        .header(HttpHeaders.AUTHORIZATION, staff.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/{id}", resources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, staff.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/queue-entries/{id}/call-next", resources.queueEntryId())
                        .header(HttpHeaders.AUTHORIZATION, staff.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("CALLED"));

        assertForbidden(mockMvc.perform(post("/api/services")
                .header(HttpHeaders.AUTHORIZATION, staff.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(serviceRequest("staff-denied-service")))));

        assertForbidden(mockMvc.perform(get("/api/reports/daily-summary")
                .header(HttpHeaders.AUTHORIZATION, staff.bearer())
                .param("date", LocalDate.now().toString())));

        assertForbidden(mockMvc.perform(put("/api/admin/users/{id}/role", customer.userId())
                .header(HttpHeaders.AUTHORIZATION, staff.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleName\":\"BUSINESS_OWNER\"}")));

        assertOperationalUpdateCannotTransferOwner(staff, resources, other.userId(), "staff");
    }

    @Test
    void businessOwnerCanOperateAndManageServicesAndReportsButCannotAssignRoles() throws Exception {
        LoginIdentity owner = registerAndLogin("business-owner", RoleName.BUSINESS_OWNER);
        LoginIdentity customer = registerAndLogin("owner-customer", RoleName.CUSTOMER);
        LoginIdentity other = registerAndLogin("owner-other", RoleName.CUSTOMER);
        ResourceSet resources = createResourceSet(customer.userId(), "owner-resources");

        mockMvc.perform(get("/api/vehicles/{id}", resources.primaryVehicleId())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/{id}", resources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/queue-entries/{id}/call-next", resources.queueEntryId())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(serviceRequest("owner-managed-service"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reports/daily-summary")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk());

        assertForbidden(mockMvc.perform(put("/api/admin/users/{id}/role", customer.userId())
                .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleName\":\"STAFF\"}")));

        assertOperationalUpdateCannotTransferOwner(owner, resources, other.userId(), "owner");
    }

    @Test
    void platformAdministratorCanManageUsersRolesAndOperationalApisButCannotRemoveLastAdministrator() throws Exception {
        LoginIdentity administrator = registerAndLogin("platform-admin", RoleName.PLATFORM_ADMIN);
        LoginIdentity customer = registerAndLogin("admin-customer", RoleName.CUSTOMER);
        LoginIdentity other = registerAndLogin("admin-other", RoleName.CUSTOMER);
        ResourceSet resources = createResourceSet(customer.userId(), "admin-resources");

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, administrator.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/users/{id}/role", customer.userId())
                        .header(HttpHeaders.AUTHORIZATION, administrator.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"STAFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").value("STAFF"));

        mockMvc.perform(get("/api/vehicles")
                        .header(HttpHeaders.AUTHORIZATION, administrator.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, administrator.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/queue-entries")
                        .header(HttpHeaders.AUTHORIZATION, administrator.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, administrator.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(serviceRequest("admin-managed-service"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reports/daily-summary")
                        .header(HttpHeaders.AUTHORIZATION, administrator.bearer())
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk());

        assertOperationalUpdateCannotTransferOwner(administrator, resources, other.userId(), "administrator");

        mockMvc.perform(delete("/api/users/{id}", administrator.userId())
                        .header(HttpHeaders.AUTHORIZATION, administrator.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("The last active platform administrator cannot be deleted"));

        mockMvc.perform(put("/api/admin/users/{id}/role", administrator.userId())
                        .header(HttpHeaders.AUTHORIZATION, administrator.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"CUSTOMER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("The last active platform administrator cannot be demoted"));
    }

    @Test
    void customerBookingUpdatesPreserveOwnerAndRejectAnotherCustomersVehicle() throws Exception {
        LoginIdentity customer = registerAndLogin("booking-customer", RoleName.CUSTOMER);
        LoginIdentity other = registerAndLogin("booking-other", RoleName.CUSTOMER);
        ResourceSet resources = createResourceSet(customer.userId(), "booking-customer-resources");
        ResourceSet otherResources = createResourceSet(other.userId(), "booking-other-resources");

        Map<String, Object> allowedUpdate = updateBookingRequest(
                resources.alternateVehicleId(),
                resources.serviceId(),
                nextScheduledTime(),
                "customer update"
        );

        mockMvc.perform(put("/api/bookings/{id}", resources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allowedUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userId").value(customer.userId()))
                .andExpect(jsonPath("$.vehicle.vehicleId").value(resources.alternateVehicleId()));

        assertEquals(customer.userId(), bookings.findById(resources.bookingId()).getUser().getUserId());

        Map<String, Object> rejectedUpdate = updateBookingRequest(
                otherResources.primaryVehicleId(),
                resources.serviceId(),
                nextScheduledTime(),
                "attempted cross-owner vehicle"
        );

        mockMvc.perform(put("/api/bookings/{id}", resources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectedUpdate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vehicle does not belong to booking owner"));

        assertEquals(customer.userId(), bookings.findById(resources.bookingId()).getUser().getUserId());
        assertEquals(resources.alternateVehicleId(),
                bookings.findById(resources.bookingId()).getVehicle().getVehicleId());
    }

    @Test
    void roleChangesInvalidateOldTokensAndNewLoginUsesCurrentCatalogueAuthorities() throws Exception {
        LoginIdentity customer = registerAndLogin("role-change", RoleName.CUSTOMER);

        users.assignRole(customer.userId(), RoleName.STAFF);

        assertUnauthorized(mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())));

        String newToken = login(customer.email());

        mockMvc.perform(get("/api/vehicles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newToken)))
                .andExpect(status().isOk());
    }

    @Test
    void injectedPermissionClaimCannotGrantAdditionalAccess() throws Exception {
        LoginIdentity customer = registerAndLogin("injected-permission", RoleName.CUSTOMER);
        String injectedToken = signedTokenWithInjectedPermissions(
                customer.userId(),
                RoleName.CUSTOMER.name(),
                List.of("ROLE_ASSIGN", "SERVICE_MANAGE", "USER_ADMIN")
        );

        assertForbidden(mockMvc.perform(post("/api/services")
                .header(HttpHeaders.AUTHORIZATION, bearer(injectedToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(serviceRequest("injected-permission-service")))));

        assertForbidden(mockMvc.perform(put("/api/admin/users/{id}/role", customer.userId())
                .header(HttpHeaders.AUTHORIZATION, bearer(injectedToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleName\":\"PLATFORM_ADMIN\"}")));
    }

    @Test
    void unknownRoleClaimIsRejectedAsUnauthorized() throws Exception {
        LoginIdentity customer = registerAndLogin("unknown-role", RoleName.CUSTOMER);
        String unknownRoleToken = signedTokenWithInjectedPermissions(
                customer.userId(),
                "SUPER_ADMIN",
                List.of("USER_ADMIN", "ROLE_ASSIGN")
        );

        assertUnauthorized(mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(unknownRoleToken))));
    }

    @Test
    void authenticationAndAuthorizationErrorsUseSafeJsonContracts() throws Exception {
        assertUnauthorized(mockMvc.perform(get("/api/services")));

        LoginIdentity customer = registerAndLogin("safe-error", RoleName.CUSTOMER);
        assertForbidden(mockMvc.perform(post("/api/services")
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(serviceRequest("safe-error-service")))));
    }

    private void assertOperationalUpdateCannotTransferOwner(
            LoginIdentity operator,
            ResourceSet resources,
            String attemptedOwnerId,
            String label
    ) throws Exception {
        Map<String, Object> allowedRequest = updateBookingRequest(
                resources.alternateVehicleId(),
                resources.serviceId(),
                nextScheduledTime(),
                label + " operational update"
        );

        mockMvc.perform(put("/api/bookings/{id}", resources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, operator.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allowedRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(resources.bookingId()))
                .andExpect(jsonPath("$.user.userId").value(resources.ownerId()));

        Map<String, Object> ownershipTransferRequest = new LinkedHashMap<>(allowedRequest);
        ownershipTransferRequest.put("userId", attemptedOwnerId);

        mockMvc.perform(put("/api/bookings/{id}", resources.bookingId())
                        .header(HttpHeaders.AUTHORIZATION, operator.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownershipTransferRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed or invalid request body"));

        assertEquals(resources.ownerId(), bookings.findById(resources.bookingId()).getUser().getUserId());
    }

    private LoginIdentity registerAndLogin(String label, RoleName roleName) throws Exception {
        String suffix = UUID.randomUUID().toString();
        String userId = label + "-" + suffix;
        String email = userId.toLowerCase(Locale.ROOT) + "@example.com";

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId,
                                "fullName", "RBAC Test User",
                                "email", email,
                                "phone", "0821234567",
                                "password", PASSWORD
                        ))))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("encodedPassword"))));

        if (roleName != RoleName.CUSTOMER) {
            users.assignRole(userId, roleName);
        }

        return new LoginIdentity(userId, email, login(email));
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asString();
    }

    private ResourceSet createResourceSet(String ownerId, String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String primaryVehicleId = label + "-vehicle-primary-" + suffix;
        String alternateVehicleId = label + "-vehicle-alternate-" + suffix;
        String serviceId = label + "-service-" + suffix;
        String bookingId = label + "-booking-" + suffix;
        String queueEntryId = label + "-queue-" + suffix;

        Vehicle primaryVehicle = vehicles.createVehicle(
                new Vehicle(primaryVehicleId, "PLATE-" + suffix, "SUV", "Toyota", "Rav4", "Black", ""),
                ownerId
        );
        vehicles.createVehicle(
                new Vehicle(alternateVehicleId, "ALT-" + suffix, "Sedan", "Honda", "Civic", "White", ""),
                ownerId
        );

        Service service = services.createService(
                new Service(serviceId, "RBAC Wash", "authorization fixture", BigDecimal.valueOf(200), 30)
        );

        Booking booking = bookings.createBooking(new Booking(
                bookingId,
                users.findById(ownerId),
                primaryVehicle,
                service,
                nextScheduledTime(),
                "authorization fixture"
        ));

        queues.createQueueEntry(new QueueEntry(queueEntryId, booking, service, 1));

        return new ResourceSet(
                ownerId,
                primaryVehicleId,
                alternateVehicleId,
                serviceId,
                bookingId,
                queueEntryId
        );
    }

    private Map<String, Object> updateBookingRequest(
            String vehicleId,
            String serviceId,
            LocalDateTime scheduledDateTime,
            String specialRequest
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("vehicleId", vehicleId);
        request.put("serviceId", serviceId);
        request.put("scheduledDateTime", scheduledDateTime.toString());
        request.put("specialRequest", specialRequest);
        return request;
    }

    private Map<String, Object> serviceRequest(String label) {
        return Map.of(
                "serviceId", label + "-" + UUID.randomUUID(),
                "serviceName", "Managed Service",
                "description", "RBAC service management test",
                "price", BigDecimal.valueOf(120),
                "estimatedDurationMin", 25
        );
    }

    private LocalDateTime nextScheduledTime() {
        return LocalDateTime.now()
                .plusYears(2)
                .plusMinutes(slotSequence.incrementAndGet())
                .withNano(0);
    }

    private void assertUnauthorized(ResultActions action) throws Exception {
        String response = action
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertSafeErrorResponse(response);
    }

    private void assertForbidden(ResultActions action) throws Exception {
        String response = action
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertSafeErrorResponse(response);
    }

    private void assertSafeErrorResponse(String response) {
        String normalized = response.toLowerCase(Locale.ROOT);
        assertFalse(normalized.contains("encodedpassword"));
        assertFalse(normalized.contains("passwordhash"));
        assertFalse(normalized.contains("accesstoken"));
        assertFalse(normalized.contains("refreshtoken"));
        assertFalse(normalized.contains("secure_jwt_secret"));
        assertFalse(normalized.contains("stacktrace"));
        assertFalse(normalized.contains("java.lang."));
    }

    private String signedTokenWithInjectedPermissions(
            String subject,
            String roleClaim,
            List<String> injectedPermissions
    ) {
        Instant issuedAt = securityClock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(subject)
                .claim("role", roleClaim)
                .claim("permissions", injectedPermissions)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(jwtProperties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record LoginIdentity(String userId, String email, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    private record ResourceSet(
            String ownerId,
            String primaryVehicleId,
            String alternateVehicleId,
            String serviceId,
            String bookingId,
            String queueEntryId
    ) {
    }
}
