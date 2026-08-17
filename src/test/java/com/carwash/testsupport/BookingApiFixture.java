package com.carwash.testsupport;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.catalog.api.dto.CreateServiceOfferingRequest;
import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.vehicle.api.dto.CreateVehicleRequest;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.LocalDateTime;
import java.math.BigDecimal;

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

        CreateBusinessRequest business = api.defaultBusiness();
        CreateBranchRequest branch = api.defaultBranch();
        if (branch == null) {
            String businessId = ids.business();
            business = new CreateBusinessRequest(
                    businessId, "Test Car Wash", ids.emailFor(businessId), "+27821234567", null);
            api.createBusiness(business).andExpect(MockMvcResultMatchers.status().isCreated());
            branch = new CreateBranchRequest(
                    ids.branch(), "Test Branch", "1 Test Street", null, "Cape Town", "Western Cape",
                    "8001", "ZA", new BigDecimal("-33.9249"), new BigDecimal("18.4241"),
                    "Africa/Johannesburg", true);
            api.createBranch(business.businessId(), branch).andExpect(MockMvcResultMatchers.status().isCreated());
            api.rememberDefaultOperationalScope(business, branch);
        }
        CreateServiceOfferingRequest offering = new CreateServiceOfferingRequest(
                ids.offering(), service.serviceId(), service.price(), service.estimatedDurationMin(), 2);
        api.createServiceOffering(branch.branchId(), offering)
                .andExpect(MockMvcResultMatchers.status().isCreated());

        return new Resources(user, vehicle, service, business, branch, offering);
    }

    public CreatedBooking createBooking(LocalDateTime scheduledDateTime) throws Exception {
        Resources resources = createResources();
        CreateBookingRequest booking = BookingFixtureBuilder.valid(
                        ids, resources.user().userId(), resources.vehicle().vehicleId(),
                        resources.branch().branchId(), resources.offering().offeringId())
                .scheduledDateTime(scheduledDateTime)
                .build();
        api.createBooking(booking).andExpect(MockMvcResultMatchers.status().isCreated());
        return new CreatedBooking(resources, booking);
    }

    public record Resources(
            CreateUserRequest user,
            CreateVehicleRequest vehicle,
            CreateServiceRequest service,
            CreateBusinessRequest business,
            CreateBranchRequest branch,
            CreateServiceOfferingRequest offering
    ) {
    }

    public record CreatedBooking(Resources resources, CreateBookingRequest booking) {
    }
}
