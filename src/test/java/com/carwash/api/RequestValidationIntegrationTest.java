package com.carwash.api;

import com.carwash.api.dto.CreateBookingRequest;
import com.carwash.api.dto.CreateQueueEntryRequest;
import com.carwash.api.dto.CreateServiceRequest;
import com.carwash.api.dto.CreateUserRequest;
import com.carwash.api.dto.CreateVehicleRequest;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestValidationIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void everyRequestContractRejectsInvalidInput() throws Exception {
        assertValidation(post("/api/users").with(anonymous()), Map.of(
                "userId", ids.user(), "fullName", "User", "email", "not-email",
                "phone", "1", "password", UserFixtureBuilder.DEFAULT_PASSWORD), "email");
        assertValidation(post("/api/auth/login").with(anonymous()), Map.of(
                "email", "not-email", "password", UserFixtureBuilder.DEFAULT_PASSWORD), "email");

        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());

        Map<String, Object> invalidVehicle = new HashMap<>();
        invalidVehicle.put("userId", user.userId());
        invalidVehicle.put("vehicleId", ids.vehicle());
        invalidVehicle.put("plateNumber", " ");
        invalidVehicle.put("vehicleType", "SUV");
        invalidVehicle.put("brand", "Toyota");
        invalidVehicle.put("model", "RAV4");
        invalidVehicle.put("notes", "x".repeat(501));
        assertValidation(post("/api/vehicles").with(authentication.platformAdminJwt()), invalidVehicle, "notes");

        CreateVehicleRequest vehicle = VehicleFixtureBuilder.valid(ids, user.userId()).build();
        api.createVehicle(vehicle).andExpect(status().isCreated());
        assertValidation(put("/api/vehicles/{id}", vehicle.vehicleId()).with(authentication.platformAdminJwt()), Map.of(
                "plateNumber", ids.plate(), "vehicleType", "SUV", "brand", "Toyota",
                "model", " ", "color", "Black", "notes", "ok"), "model");

        assertValidation(post("/api/services").with(authentication.platformAdminJwt()), Map.of(
                "serviceId", ids.service(), "serviceName", "Wash",
                "description", "", "price", 0, "estimatedDurationMin", 0), "estimatedDurationMin");

        CreateServiceRequest service = ServiceFixtureBuilder.valid(ids).build();
        api.createService(service).andExpect(status().isCreated());
        assertValidation(put("/api/services/{id}", service.serviceId()).with(authentication.platformAdminJwt()), Map.of(
                "serviceName", "Wash", "description", "", "price", -1,
                "estimatedDurationMin", 30), "price");

        Map<String, Object> invalidBooking = new HashMap<>();
        invalidBooking.put("bookingId", ids.booking());
        invalidBooking.put("userId", user.userId());
        invalidBooking.put("vehicleId", vehicle.vehicleId());
        invalidBooking.put("serviceId", service.serviceId());
        invalidBooking.put("specialRequest", "x".repeat(1001));
        assertValidation(post("/api/bookings").with(authentication.platformAdminJwt()), invalidBooking, "scheduledDateTime");

        CreateBookingRequest booking = BookingFixtureBuilder.valid(ids, user.userId(), vehicle.vehicleId(), service.serviceId())
                .scheduledDateTime(TestDates.futureDays(30)).build();
        api.createBooking(booking).andExpect(status().isCreated());
        Map<String, Object> invalidBookingUpdate = new HashMap<>();
        invalidBookingUpdate.put("vehicleId", vehicle.vehicleId());
        invalidBookingUpdate.put("serviceId", " ");
        invalidBookingUpdate.put("scheduledDateTime", TestDates.futureDays(31).toString());
        invalidBookingUpdate.put("specialRequest", "ok");
        assertValidation(put("/api/bookings/{id}", booking.bookingId()).with(authentication.platformAdminJwt()),
                invalidBookingUpdate, "serviceId");

        assertValidation(post("/api/queue-entries").with(authentication.platformAdminJwt()), Map.of(
                "queueEntryId", ids.queueEntry(), "bookingId", booking.bookingId(),
                "serviceId", service.serviceId(), "position", 0), "position");

        mockMvc.perform(post("/api/bookings/{id}/confirm", booking.bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        CreateQueueEntryRequest queue = QueueFixtureBuilder.valid(ids, booking.bookingId(), service.serviceId()).build();
        api.createQueueEntry(queue).andExpect(status().isCreated());
        assertValidation(put("/api/queue-entries/{id}/position", queue.queueEntryId())
                .with(authentication.platformAdminJwt()), Map.of("position", -1), "position");

        assertValidation(put("/api/admin/users/{userId}/role", user.userId())
                .with(authentication.platformAdminJwt()), Map.of(), "roleName");
    }

    private void assertValidation(MockHttpServletRequestBuilder request, Map<String, Object> body, String field)
            throws Exception {
        var action = mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == '" + field + "')]").exists());
        ApiContractAssertions.assertNoSensitiveData(action, false);
    }
}
