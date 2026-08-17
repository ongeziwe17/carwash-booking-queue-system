package com.carwash.discovery.api;

import com.carwash.discovery.api.dto.NearbyBranchResponse;
import com.carwash.discovery.application.NearbyBranchDiscoveryService;
import com.carwash.discovery.application.NearbyBranchSearchCriteria;
import com.carwash.discovery.application.NearbyBranchSnapshot;
import com.carwash.discovery.application.NearbyBranchSort;
import com.carwash.shared.api.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@Validated
@Tag(name = "Marketplace Branch Discovery",
        description = "Authenticated straight-line distance discovery for effective public Marketplace branches.")
@RequestMapping("/api/marketplace/branches/discoverable")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid discovery parameter",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Marketplace read permission missing",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Requested reusable service not found",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "405", description = "Method not allowed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class NearbyBranchDiscoveryController {

    private final NearbyBranchDiscoveryService discovery;

    public NearbyBranchDiscoveryController(NearbyBranchDiscoveryService discovery) {
        this.discovery = discovery;
    }

    @GetMapping("/nearby")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_READ')")
    @Operation(summary = "Discover nearby Marketplace branches",
            description = "Returns only effective active, public-discovery-enabled branches. Radius filtering and distance sorting use the unrounded Haversine distance; responses use kilometres rounded to two decimals with HALF_UP. When sort is omitted results use branch-ID order. Equal distances use branch ID as the tie-breaker. serviceId requires a discoverable effective branch offering, and openAt reuses the branch scheduling decision for the supplied instant.")
    @ApiResponse(responseCode = "200", description = "Nearby branches returned")
    public List<NearbyBranchResponse> findNearby(
            @Parameter(required = true, description = "Origin latitude in decimal degrees (-90 to 90).")
            @RequestParam @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @Parameter(required = true, description = "Origin longitude in decimal degrees (-180 to 180).")
            @RequestParam @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @Parameter(description = "Optional inclusive radius in kilometres, greater than zero and at most 20000.")
            @RequestParam(required = false) @DecimalMin(value = "0.0", inclusive = false)
            @DecimalMax("20000.0") BigDecimal radiusKm,
            @Parameter(description = "Optional reusable service ID (1-64 characters).")
            @RequestParam(required = false) @Size(max = 64) String serviceId,
            @Parameter(description = "Optional RFC 3339 instant; only branches open at this exact instant are returned.")
            @RequestParam(required = false) OffsetDateTime openAt,
            @Parameter(description = "Optional sort mode. The only supported value is 'distance'.")
            @RequestParam(required = false) @Size(max = 16) String sort
    ) {
        return discovery.findNearby(new NearbyBranchSearchCriteria(
                        latitude,
                        longitude,
                        radiusKm,
                        serviceId,
                        openAt == null ? null : openAt.toInstant(),
                        NearbyBranchSort.fromApiValue(sort)))
                .stream()
                .map(NearbyBranchDiscoveryController::toResponse)
                .toList();
    }

    private static NearbyBranchResponse toResponse(NearbyBranchSnapshot branch) {
        return new NearbyBranchResponse(
                branch.branchId(), branch.businessId(), branch.branchName(), branch.addressLine1(),
                branch.addressLine2(), branch.city(), branch.province(), branch.postalCode(),
                branch.countryCode(), branch.latitude(), branch.longitude(), branch.timezone(), branch.distanceKm());
    }
}
