package com.carwash.booking.api;

import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.booking.api.dto.RescheduleBookingRequest;
import com.carwash.booking.api.dto.UpdateBookingRequest;
import com.carwash.booking.domain.Booking;
import com.carwash.booking.application.BookingManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@Tag(name = "Bookings", description = "Operations for managing bookings.")
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingManagementService service;

    public BookingController(BookingManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(summary = "List bookings")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bookings returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<Booking> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.canAccessBooking(authentication, #id)")
    @Operation(summary = "Get booking by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking returned"),
            @ApiResponse(responseCode = "400", description = "Invalid identifier",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Booking getById(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize("@resourceAuthorization.canCreateFor(authentication, #req.userId())")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create booking")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Booking created"),
            @ApiResponse(responseCode = "400", description = "Invalid request or booking rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Referenced resource not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Booking create(@Valid @RequestBody CreateBookingRequest req) {
        return service.createBooking(
                req.bookingId(),
                req.userId(),
                req.vehicleId(),
                req.serviceId(),
                req.scheduledDateTime(),
                req.specialRequest()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.canAccessBooking(authentication, #id)")
    @Operation(
            summary = "Update booking",
            description = "Updates non-schedule details for a CREATED booking or a CONFIRMED booking without an "
                    + "active queue entry. Schedule changes must use POST /api/bookings/{id}/reschedule."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request or booking rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking or referenced resource not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Booking update(
            @PathVariable @NotBlank @Size(max = 64) String id,
            @Valid @RequestBody UpdateBookingRequest request
    ) {
        return service.updateBooking(
                id,
                request.vehicleId(),
                request.serviceId(),
                request.specialRequest()
        );
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("@resourceAuthorization.canAccessBooking(authentication, #id)")
    @Operation(
            summary = "Reschedule booking",
            description = "Reschedules an eligible future CREATED or CONFIRMED booking while preserving its status, "
                    + "vehicle, service, and owner. The request is rejected when the booking-change cutoff has "
                    + "closed, active queue work exists, the target slot is full or conflicting, or the associated "
                    + "service is inactive."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking rescheduled"),
            @ApiResponse(responseCode = "400", description = "Invalid request or booking rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking or referenced resource not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Booking reschedule(
            @PathVariable @NotBlank @Size(max = 64) String id,
            @Valid @RequestBody RescheduleBookingRequest request
    ) {
        return service.rescheduleBooking(id, request.scheduledDateTime());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@resourceAuthorization.canAccessBooking(authentication, #id)")
    @Operation(
            summary = "Cancel booking and return no content",
            description = "Cancels an eligible CREATED or CONFIRMED booking. An associated WAITING or CALLED "
                    + "queue entry is removed and the active queue is rebalanced. IN_SERVICE bookings cannot be "
                    + "cancelled."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Booking cancelled"),
            @ApiResponse(responseCode = "400", description = "Invalid identifier or booking rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public void delete(@PathVariable @NotBlank @Size(max = 64) String id) {
        service.cancelBooking(id);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('STAFF','BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(summary = "Confirm booking")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking confirmed"),
            @ApiResponse(responseCode = "400", description = "Invalid identifier or booking rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Booking confirm(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.confirmBooking(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@resourceAuthorization.canAccessBooking(authentication, #id)")
    @Operation(
            summary = "Cancel booking",
            description = "Cancels an eligible CREATED or CONFIRMED booking. An associated WAITING or CALLED "
                    + "queue entry is removed and the active queue is rebalanced. IN_SERVICE bookings cannot be "
                    + "cancelled."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking cancelled"),
            @ApiResponse(responseCode = "400", description = "Invalid identifier or booking rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Booking cancel(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.cancelBooking(id);
    }
}
