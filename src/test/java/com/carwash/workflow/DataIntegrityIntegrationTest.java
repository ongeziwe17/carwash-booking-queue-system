package com.carwash.workflow;

import com.carwash.booking.domain.Booking;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.queue.api.dto.CreateQueueEntryRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.vehicle.api.dto.CreateVehicleRequest;
import com.carwash.testsupport.ApiContractAssertions;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingFixtureBuilder;
import com.carwash.testsupport.QueueFixtureBuilder;
import com.carwash.testsupport.ServiceFixtureBuilder;
import com.carwash.testsupport.TestDates;
import com.carwash.testsupport.UserFixtureBuilder;
import com.carwash.testsupport.VehicleFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DataIntegrityIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void duplicateIdsReturnBusinessRuleViolationWithoutReplacingData() throws Exception {
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());
        CreateUserRequest duplicateUser = new CreateUserRequest(user.userId(), "Replacement",
                ids.emailFor(ids.user()), user.phone(), user.password());
        assertBusinessRule(api.createUser(duplicateUser), "/api/users", "User ID already exists");

        CreateVehicleRequest vehicle = VehicleFixtureBuilder.valid(ids, user.userId()).build();
        api.createVehicle(vehicle).andExpect(status().isCreated());
        CreateVehicleRequest duplicateVehicle = new CreateVehicleRequest(user.userId(), vehicle.vehicleId(), ids.plate(),
                vehicle.vehicleType(), "Replacement", vehicle.model(), vehicle.color(), vehicle.notes());
        assertBusinessRule(api.createVehicle(duplicateVehicle), "/api/vehicles", "Vehicle ID already exists");
        mockMvc.perform(get("/api/vehicles/{id}", vehicle.vehicleId()).with(authentication.platformAdminJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plateNumber").value(vehicle.plateNumber()));

        CreateServiceRequest service = ServiceFixtureBuilder.valid(ids).build();
        api.createService(service).andExpect(status().isCreated());
        CreateServiceRequest duplicateService = new CreateServiceRequest(service.serviceId(), "Replacement",
                service.description(), service.price(), service.estimatedDurationMin());
        assertBusinessRule(api.createService(duplicateService), "/api/services", "Service ID already exists");

        CreateBookingRequest booking = BookingFixtureBuilder.valid(ids, user.userId(), vehicle.vehicleId(), service.serviceId())
                .scheduledDateTime(TestDates.futureDays(40)).build();
        api.createBooking(booking).andExpect(status().isCreated());
        CreateBookingRequest duplicateBooking = new CreateBookingRequest(booking.bookingId(), booking.userId(),
                booking.vehicleId(), booking.serviceId(), booking.scheduledDateTime(), "Replacement");
        assertBusinessRule(api.createBooking(duplicateBooking), "/api/bookings", "Booking ID already exists");
        mockMvc.perform(post("/api/bookings/{id}/confirm", booking.bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());

        CreateQueueEntryRequest queue = QueueFixtureBuilder.valid(ids, booking.bookingId(), service.serviceId()).build();
        api.createQueueEntry(queue).andExpect(status().isCreated());
        CreateBookingRequest secondBooking = BookingFixtureBuilder.valid(
                        ids, user.userId(), vehicle.vehicleId(), service.serviceId())
                .scheduledDateTime(TestDates.futureDays(41)).build();
        api.createBooking(secondBooking).andExpect(status().isCreated());
        mockMvc.perform(post("/api/bookings/{id}/confirm", secondBooking.bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        CreateQueueEntryRequest duplicateQueue = new CreateQueueEntryRequest(queue.queueEntryId(),
                secondBooking.bookingId(), queue.serviceId());
        assertBusinessRule(api.createQueueEntry(duplicateQueue), "/api/queue-entries", "Queue entry ID already exists");
        mockMvc.perform(get("/api/queue-entries/{id}", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.bookingId").value(booking.bookingId()));
    }

    @Test
    void referencedResourcesReturnStandardDependencyViolationContract() throws Exception {
        Fixture fixture = createFixture(TestDates.futureDays(41));
        assertBusinessRule(mockMvc.perform(delete("/api/users/{id}", fixture.user.userId())
                        .with(authentication.platformAdminJwt())), "/api/users/" + fixture.user.userId(),
                "User cannot be deleted while vehicles or bookings still reference it");
        assertBusinessRule(mockMvc.perform(delete("/api/vehicles/{id}", fixture.vehicle.vehicleId())
                        .with(authentication.platformAdminJwt())), "/api/vehicles/" + fixture.vehicle.vehicleId(),
                "Vehicle cannot be deleted while bookings still reference it");
        assertBusinessRule(mockMvc.perform(delete("/api/services/{id}", fixture.service.serviceId())
                        .with(authentication.platformAdminJwt())), "/api/services/" + fixture.service.serviceId(),
                "Referenced service cannot be deleted; deactivate it instead");
    }

    @Test
    void terminalBookingAndQueueMutationsReturnBusinessRuleViolation() throws Exception {
        Fixture fixture = createFixture(TestDates.futureDays(42));
        mockMvc.perform(post("/api/bookings/{id}/confirm", fixture.booking.bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        CreateQueueEntryRequest queue = QueueFixtureBuilder.valid(ids, fixture.booking.bookingId(), fixture.service.serviceId()).build();
        api.createQueueEntry(queue).andExpect(status().isCreated());
        mockMvc.perform(post("/api/queue-entries/{id}/call", queue.queueEntryId())
                        .with(authentication.platformAdminJwt())).andExpect(status().isOk());
        assertBusinessRule(mockMvc.perform(put("/api/queue-entries/{id}/position", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":2}")),
                "/api/queue-entries/" + queue.queueEntryId() + "/position", "Only waiting queue entries can be repositioned");
        assertBusinessRule(mockMvc.perform(delete("/api/queue-entries/{id}", queue.queueEntryId())
                        .with(authentication.platformAdminJwt())),
                "/api/queue-entries/" + queue.queueEntryId(), "Only waiting queue entries can be deleted");

        mockMvc.perform(post("/api/bookings/{id}/cancel", fixture.booking.bookingId())
                        .with(authentication.platformAdminJwt())).andExpect(status().isOk());
        assertBusinessRule(mockMvc.perform(put("/api/bookings/{id}", fixture.booking.bookingId())
                        .with(authentication.platformAdminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", fixture.vehicle.vehicleId(), "serviceId", fixture.service.serviceId(),
                                "specialRequest", "Updated")))),
                "/api/bookings/" + fixture.booking.bookingId(), "Booking cannot be updated in its current state");
    }

    private Fixture createFixture(java.time.LocalDateTime scheduled) throws Exception {
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());
        CreateVehicleRequest vehicle = VehicleFixtureBuilder.valid(ids, user.userId()).build();
        api.createVehicle(vehicle).andExpect(status().isCreated());
        CreateServiceRequest service = ServiceFixtureBuilder.valid(ids).build();
        api.createService(service).andExpect(status().isCreated());
        CreateBookingRequest booking = BookingFixtureBuilder.valid(ids, user.userId(), vehicle.vehicleId(), service.serviceId())
                .scheduledDateTime(scheduled).build();
        api.createBooking(booking).andExpect(status().isCreated());
        return new Fixture(user, vehicle, service, booking);
    }

    private void assertBusinessRule(org.springframework.test.web.servlet.ResultActions action, String path, String message)
            throws Exception {
        action.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.fieldErrors").isArray());
        ApiContractAssertions.assertNoSensitiveData(action, false);
    }

    private record Fixture(CreateUserRequest user, CreateVehicleRequest vehicle,
                           CreateServiceRequest service, CreateBookingRequest booking) {}
}
