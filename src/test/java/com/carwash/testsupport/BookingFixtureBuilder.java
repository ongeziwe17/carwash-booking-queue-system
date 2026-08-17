package com.carwash.testsupport;

import com.carwash.booking.api.dto.CreateBookingRequest;

import java.time.LocalDateTime;

public final class BookingFixtureBuilder {

    private String bookingId;
    private String userId;
    private String vehicleId;
    private String branchId;
    private String serviceOfferingId;
    private LocalDateTime scheduledDateTime = TestDates.future();
    private String specialRequest = "integration test";

    private BookingFixtureBuilder() {
    }

    public static BookingFixtureBuilder valid(
            TestIdFactory ids,
            String userId,
            String vehicleId,
            String branchId,
            String serviceOfferingId
    ) {
        BookingFixtureBuilder builder = new BookingFixtureBuilder();
        builder.bookingId = ids.booking();
        builder.userId = userId;
        builder.vehicleId = vehicleId;
        builder.branchId = branchId;
        builder.serviceOfferingId = serviceOfferingId;
        return builder;
    }

    public BookingFixtureBuilder bookingId(String value) { this.bookingId = value; return this; }
    public BookingFixtureBuilder userId(String value) { this.userId = value; return this; }
    public BookingFixtureBuilder vehicleId(String value) { this.vehicleId = value; return this; }
    public BookingFixtureBuilder branchId(String value) { this.branchId = value; return this; }
    public BookingFixtureBuilder serviceOfferingId(String value) { this.serviceOfferingId = value; return this; }
    public BookingFixtureBuilder scheduledDateTime(LocalDateTime value) { this.scheduledDateTime = value; return this; }
    public BookingFixtureBuilder specialRequest(String value) { this.specialRequest = value; return this; }

    public CreateBookingRequest build() {
        return new CreateBookingRequest(
                bookingId, userId, vehicleId, branchId, serviceOfferingId, scheduledDateTime, specialRequest);
    }
}
