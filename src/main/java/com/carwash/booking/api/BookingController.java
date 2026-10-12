package com.carwash.booking.api;

import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.booking.api.dto.RescheduleBookingRequest;
import com.carwash.booking.api.dto.UpdateBookingRequest;
import com.carwash.booking.domain.Booking;
import com.carwash.booking.application.BookingManagementService;
import com.carwash.access.application.TenantAccessContextProvider;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.carwash.shared.api.PageResponse;

@RestController
@Validated
@Tag(name = "Bookings", description = "Operations for managing bookings.")
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingManagementService service;
    private final TenantAccessContextProvider tenantAccess;

    public BookingController(BookingManagementService service, TenantAccessContextProvider tenantAccess) {
        this.service = service;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(
            summary = "List bookings",
            description = "Tenant operators receive only their authenticated business scope. Platform administrators "
                    + "must provide an explicit businessId; branchId may narrow that business scope."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bookings returned", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Invalid branch filter",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Branch not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @io.swagger.v3.oas.annotations.Parameters({
        @io.swagger.v3.oas.annotations.Parameter(name = "id", description = "Exact resource ID; narrows authorized scope only.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", maxLength = 64)),
        @io.swagger.v3.oas.annotations.Parameter(name = "page", description = "Zero-based page; default 0. Invalid or overflowing offsets return ApiErrorResponse 400.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", minimum = "0", defaultValue = "0")),
        @io.swagger.v3.oas.annotations.Parameter(name = "size", description = "Page size, default 20; rejects values outside 1-100.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")),
        @io.swagger.v3.oas.annotations.Parameter(name = "sort", description = "Repeat up to three field,asc|desc terms from the endpoint allowlist; unique ID tie-breaker and NULLS LAST. See API-PAGINATION.md.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
        @io.swagger.v3.oas.annotations.Parameter(name = "status", description = "Exact documented lifecycle state.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
        @io.swagger.v3.oas.annotations.Parameter(name = "serviceId", description = "Exact service ID.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
        @io.swagger.v3.oas.annotations.Parameter(name = "offeringId", description = "Exact offering ID.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
        @io.swagger.v3.oas.annotations.Parameter(name = "userId", description = "Exact customer ID within mandatory business scope.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
        @io.swagger.v3.oas.annotations.Parameter(name = "from", description = "Inclusive branch-local scheduled time with full nanosecond precision; no offset.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
        @io.swagger.v3.oas.annotations.Parameter(name = "to", description = "Inclusive branch-local scheduled time, must not precede from.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    })
    public PageResponse<com.carwash.booking.api.dto.BookingListResponse> getAll(
            @RequestParam(required = false) @Size(max = 64) String branchId,
            @RequestParam(required = false) @Size(max = 64) String businessId,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam org.springframework.util.MultiValueMap<String, String> parameters
    ) {
        return PageResponse.from(service.findPage(tenantAccess.current(), branchId, businessId, com.carwash.booking.application.BookingListPolicy.POLICY.parse(parameters)).map(com.carwash.booking.api.dto.BookingListResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_BOOKING_SELF_MANAGE','PERM_BOOKING_OPERATE')")
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
        return service.findById(tenantAccess.current(), id);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_BOOKING_SELF_MANAGE','PERM_BOOKING_OPERATE')")
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
                tenantAccess.current(),
                req.bookingId(),
                req.userId(),
                req.vehicleId(),
                req.branchId(),
                req.serviceOfferingId(),
                req.scheduledDateTime(),
                req.specialRequest()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_BOOKING_SELF_MANAGE','PERM_BOOKING_OPERATE')")
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
                tenantAccess.current(),
                id,
                request.vehicleId(),
                request.serviceOfferingId(),
                request.specialRequest()
        );
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyAuthority('PERM_BOOKING_SELF_MANAGE','PERM_BOOKING_OPERATE')")
    @Operation(
            summary = "Reschedule booking",
            description = "Reschedules an eligible future CREATED or CONFIRMED booking while preserving its status, "
                    + "vehicle, branch, service offering, and owner. The request is rejected when the booking-change cutoff has "
                    + "closed, active queue work exists, the target slot is full or conflicting, or the associated "
                    + "branch, offering, or reusable service is inactive."
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
        return service.rescheduleBooking(tenantAccess.current(), id, request.scheduledDateTime());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('PERM_BOOKING_SELF_MANAGE','PERM_BOOKING_OPERATE')")
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
        service.cancelBooking(tenantAccess.current(), id);
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
        return service.confirmBooking(tenantAccess.current(), id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('PERM_BOOKING_SELF_MANAGE','PERM_BOOKING_OPERATE')")
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
        return service.cancelBooking(tenantAccess.current(), id);
    }
}
