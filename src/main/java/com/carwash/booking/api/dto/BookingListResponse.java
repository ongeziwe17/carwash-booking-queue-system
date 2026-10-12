package com.carwash.booking.api.dto;
import com.carwash.booking.application.BookingSnapshot;
import com.carwash.booking.domain.BookingStatus;
import java.time.LocalDateTime;
public record BookingListResponse(String bookingId, String userId, String vehicleId, String branchId,
                                  String serviceOfferingId, String serviceId, LocalDateTime scheduledDateTime,
                                  BookingStatus status, LocalDateTime createdAt) {
    public static BookingListResponse from(BookingSnapshot s) { return new BookingListResponse(s.bookingId(), s.userId(),
            s.vehicleId(), s.branchId(), s.serviceOfferingId(), s.serviceId(), s.scheduledDateTime(), s.status(), s.createdAt()); }
}
