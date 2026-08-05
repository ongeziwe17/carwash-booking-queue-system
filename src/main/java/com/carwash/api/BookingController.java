package com.carwash.api;

import com.carwash.api.dto.ApiErrorResponse;
import com.carwash.api.dto.CreateBookingRequest;
import com.carwash.api.dto.UpdateBookingRequest;
import com.carwash.domain.Booking;
import com.carwash.service.BookingManagementService;
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
import org.springframework.web.bind.annotation.*;

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
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<Booking> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.canAccessBooking(authentication, #id)")
    @Operation(summary = "Get booking by ID")
    public Booking getById(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize("@resourceAuthorization.canCreateFor(authentication, #req.userId())")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create booking")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Booking created"),
            @ApiResponse(responseCode = "400", description = "Invalid request or booking rule violation", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Referenced resource not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Booking create(@Valid @RequestBody CreateBookingRequest req) {
        return service.createBooking(req.bookingId(), req.userId(), req.vehicleId(), req.serviceId(),
                req.scheduledDateTime(), req.specialRequest());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.canAccessBooking(authentication, #id)")
    @Operation(summary = "Update booking")
    public Booking update(
            @PathVariable @NotBlank @Size(max = 64) String id,
            @Valid @RequestBody UpdateBookingRequest request
    ) {
        return service.updateBooking(id, request.vehicleId(), request.serviceId(),
                request.scheduledDateTime(), request.specialRequest());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@resourceAuthorization.canAccessBooking(authentication, #id)")
    @Operation(summary = "Cancel booking and return no content")
    public void delete(@PathVariable @NotBlank @Size(max = 64) String id) {
        service.cancelBooking(id);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('STAFF','BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(summary = "Confirm booking")
    public Booking confirm(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.confirmBooking(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@resourceAuthorization.canAccessBooking(authentication, #id)")
    @Operation(summary = "Cancel booking")
    public Booking cancel(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.cancelBooking(id);
    }
}
