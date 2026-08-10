package com.carwash.api;

import com.carwash.api.dto.ApiErrorResponse;
import com.carwash.api.dto.AvailabilityRequest;
import com.carwash.api.dto.ServiceAvailabilityResponse;
import com.carwash.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Availability", description = "Single-location service availability operations.")
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
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
}
