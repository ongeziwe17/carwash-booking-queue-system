package com.carwash.testsupport;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.vehicle.api.dto.CreateVehicleRequest;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.LocalDateTime;

public final class BookingApiFixture {

    private final ApiTestClient api;
    private final TestIdFactory ids;

    public BookingApiFixture(ApiTestClient api, TestIdFactory ids) {
        this.api = api;
        this.ids = ids;
    }

    public Resources createResources() throws Exception {
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(MockMvcResultMatchers.status().isCreated());

        CreateVehicleRequest vehicle = VehicleFixtureBuilder.valid(ids, user.userId()).build();
        api.createVehicle(vehicle).andExpect(MockMvcResultMatchers.status().isCreated());

        CreateServiceRequest service = ServiceFixtureBuilder.valid(ids).build();
        api.createService(service).andExpect(MockMvcResultMatchers.status().isCreated());
        api.activateService(service.serviceId()).andExpect(MockMvcResultMatchers.status().isOk());

        return new Resources(user, vehicle, service);
    }

    public CreatedBooking createBooking(LocalDateTime scheduledDateTime) throws Exception {
        Resources resources = createResources();
        CreateBookingRequest booking = BookingFixtureBuilder.valid(
                        ids, resources.user().userId(), resources.vehicle().vehicleId(), resources.service().serviceId())
                .scheduledDateTime(scheduledDateTime)
                .build();
        api.createBooking(booking).andExpect(MockMvcResultMatchers.status().isCreated());
        return new CreatedBooking(resources, booking);
    }

    public record Resources(
            CreateUserRequest user,
            CreateVehicleRequest vehicle,
            CreateServiceRequest service
    ) {
    }

    public record CreatedBooking(Resources resources, CreateBookingRequest booking) {
    }
}
