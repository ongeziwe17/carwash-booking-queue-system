package com.carwash.recommendation.api;

import com.carwash.recommendation.api.dto.BranchRecommendationResponse;
import com.carwash.recommendation.api.dto.RecommendationScoreBreakdownResponse;
import com.carwash.recommendation.api.dto.RecommendationScoreComponentResponse;
import com.carwash.recommendation.application.RecommendationPreference;
import com.carwash.recommendation.application.RecommendationScoreBreakdownSnapshot;
import com.carwash.recommendation.application.RecommendationScoreComponentSnapshot;
import com.carwash.recommendation.application.RecommendationSearchCriteria;
import com.carwash.recommendation.application.RecommendationService;
import com.carwash.recommendation.application.RecommendationSnapshot;
import com.carwash.shared.api.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
@Tag(name = "Marketplace Recommendations",
        description = "Authenticated, deterministic, explainable recommendations over booking-valid candidates.")
@RequestMapping("/api/recommendations")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid recommendation parameter",
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
public class RecommendationController {

    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @GetMapping("/branches")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_READ')")
    @Operation(
            summary = "Recommend eligible branch offerings",
            description = "Ranks one AVAIL-002 candidate set using raw distance, branch-local queue context, "
                    + "offering duration and price. Lower-is-better metrics use min-max normalization. Missing "
                    + "future-date queue and total-time values remain null, score zero, and rank after known values. "
                    + "BEST_OVERALL uses validated weights summing to one. Internal DECIMAL128 values drive ranking; "
                    + "response scores use six decimals and distance uses two. Ties use branchId then "
                    + "serviceOfferingId. Results are point-in-time reads, do not reserve capacity, and booking "
                    + "creation authoritatively revalidates the returned branch, offering, and local start."
    )
    @ApiResponse(responseCode = "200", description = "Ranked recommendations returned",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = BranchRecommendationResponse.class))))
    public List<BranchRecommendationResponse> recommendBranches(
            @Parameter(required = true, description = "Origin latitude in decimal degrees (-90 to 90).")
            @RequestParam @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @Parameter(required = true, description = "Origin longitude in decimal degrees (-180 to 180).")
            @RequestParam @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @Parameter(required = true, description = "Reusable service ID (1-64 characters).")
            @RequestParam @NotBlank @Size(max = 64) String serviceId,
            @Parameter(required = true, description = "Desired offset-aware RFC 3339 service start.")
            @RequestParam @NotNull OffsetDateTime at,
            @Parameter(required = true, description = "Recommendation ranking objective.")
            @RequestParam @NotNull RecommendationPreference preference,
            @Parameter(description = "Optional inclusive radius in kilometres; defaults to and cannot exceed the configured server maximum.")
            @RequestParam(required = false) @DecimalMin(value = "0.0", inclusive = false)
            @DecimalMax("20000.0") BigDecimal maxRadiusKm
    ) {
        return recommendations.recommend(new RecommendationSearchCriteria(
                        latitude, longitude, serviceId, at.toInstant(), preference, maxRadiusKm))
                .stream()
                .map(RecommendationController::response)
                .toList();
    }

    private static BranchRecommendationResponse response(RecommendationSnapshot snapshot) {
        return new BranchRecommendationResponse(
                snapshot.rank(), snapshot.businessId(), snapshot.businessName(), snapshot.branchId(),
                snapshot.branchName(), snapshot.serviceOfferingId(), snapshot.serviceId(), snapshot.serviceName(),
                snapshot.branchTimezone(), snapshot.availableStartAt(), snapshot.estimatedEndAt(),
                snapshot.distanceKm(), snapshot.queueWaitEstimateMin(), snapshot.serviceDurationMin(),
                snapshot.estimatedTotalTimeMin(), snapshot.price(), snapshot.concurrentCapacity(),
                snapshot.remainingCapacity(), snapshot.appliedPreference(), snapshot.recommendationScore(),
                response(snapshot.scoreBreakdown()), snapshot.explanation());
    }

    private static RecommendationScoreBreakdownResponse response(
            RecommendationScoreBreakdownSnapshot snapshot
    ) {
        return new RecommendationScoreBreakdownResponse(
                response(snapshot.distance()), response(snapshot.queueWait()),
                response(snapshot.totalTime()), response(snapshot.price()));
    }

    private static RecommendationScoreComponentResponse response(
            RecommendationScoreComponentSnapshot snapshot
    ) {
        return new RecommendationScoreComponentResponse(
                snapshot.rawValue(), snapshot.available(), snapshot.normalizedScore(),
                snapshot.configuredWeight(), snapshot.weightedContribution());
    }
}
