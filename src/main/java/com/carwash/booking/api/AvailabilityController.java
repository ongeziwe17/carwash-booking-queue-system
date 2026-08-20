package com.carwash.booking.api;

import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.booking.api.dto.AvailabilityRequest;
import com.carwash.booking.api.dto.BranchAvailabilityResponse;
import com.carwash.booking.api.dto.ServiceAvailabilityResponse;
import com.carwash.booking.application.BranchAvailabilitySearchCriteria;
import com.carwash.booking.application.BranchAvailabilitySearchService;
import com.carwash.booking.application.BranchAvailabilitySnapshot;
import com.carwash.booking.application.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@Validated
@Tag(name = "Availability", description = "Legacy single-location and branch-aware availability operations.")
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final BranchAvailabilitySearchService branchAvailabilitySearchService;

    public AvailabilityController(
            AvailabilityService availabilityService,
            BranchAvailabilitySearchService branchAvailabilitySearchService
    ) {
        this.availabilityService = availabilityService;
        this.branchAvailabilitySearchService = branchAvailabilitySearchService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_SERVICE_READ')")
    @Operation(
            summary = "Get service availability",
            description = "Returns a point-in-time single-location capacity availability snapshot for one active "
                    + "service and ISO date. Slots follow the configured global operating window and exact-start "
                    + "interval, include remaining global capacity, and ensure the service finishes by closing. "
                    + "The response does not reserve capacity; booking creation remains authoritative and "
                    + "revalidates customer-specific rules."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Availability returned",
                    content = @Content(schema = @Schema(implementation = ServiceAvailabilityResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or inactive service",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ServiceAvailabilityResponse getAvailability(
            @Valid @ParameterObject @ModelAttribute AvailabilityRequest request
    ) {
        return availabilityService.findAvailability(request.serviceId(), request.date());
    }

    @GetMapping("/branches")
    @PreAuthorize("hasAuthority('PERM_SERVICE_READ')")
    @Operation(
            summary = "Search branch-aware availability",
            description = "Returns only publicly discoverable branches whose effective offering can serve the "
                    + "requested reusable service for the complete RFC 3339 service window. Capacity is scoped "
                    + "to overlapping active bookings for the exact branch and offering. Coordinates are optional "
                    + "but must be supplied together; radius filtering and ordering use raw Haversine distance, "
                    + "while response distance is rounded to two decimals. An instant that resolves to an "
                    + "ambiguous branch-local start during a DST overlap is excluded so every advertised start "
                    + "can be represented by the branch-local booking contract. Results do not reserve capacity."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Available branch offerings returned",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = BranchAvailabilityResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid or conflicting search parameter",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Service read permission missing",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reusable service not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<BranchAvailabilityResponse> findBranchAvailability(
            @Parameter(required = true, description = "Reusable service ID (1-64 characters).")
            @RequestParam @NotBlank @Size(max = 64) String serviceId,
            @Parameter(required = true, description = "Required RFC 3339 requested start instant.")
            @RequestParam @NotNull OffsetDateTime at,
            @Parameter(description = "Optional origin latitude; longitude must also be supplied.")
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @Parameter(description = "Optional origin longitude; latitude must also be supplied.")
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @Parameter(description = "Optional inclusive raw-distance radius in kilometres (0, 20000].")
            @RequestParam(required = false) @DecimalMin(value = "0.0", inclusive = false)
            @DecimalMax("20000.0") BigDecimal radiusKm
    ) {
        return branchAvailabilitySearchService.findAvailableBranches(new BranchAvailabilitySearchCriteria(
                        serviceId, at.toInstant(), latitude, longitude, radiusKm))
                .stream()
                .map(AvailabilityController::toResponse)
                .toList();
    }

    private static BranchAvailabilityResponse toResponse(BranchAvailabilitySnapshot snapshot) {
        return new BranchAvailabilityResponse(
                snapshot.branchId(), snapshot.businessId(), snapshot.branchName(), snapshot.addressLine1(),
                snapshot.addressLine2(), snapshot.city(), snapshot.province(), snapshot.postalCode(),
                snapshot.countryCode(), snapshot.latitude(), snapshot.longitude(), snapshot.timezone(),
                snapshot.serviceOfferingId(), snapshot.serviceId(), snapshot.serviceName(), snapshot.price(),
                snapshot.estimatedDurationMin(), snapshot.availableStartAt(), snapshot.estimatedEndAt(),
                snapshot.concurrentCapacity(), snapshot.capacityRemaining(), snapshot.queueWaitEstimateMin(),
                snapshot.distanceKm());
    }
}
