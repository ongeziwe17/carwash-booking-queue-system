package com.carwash.marketplace.api;

import com.carwash.marketplace.api.dto.BranchOpenStatusResponse;
import com.carwash.marketplace.api.dto.BranchOperatingHoursResponse;
import com.carwash.marketplace.api.dto.CreateTemporaryClosureRequest;
import com.carwash.marketplace.api.dto.ReplaceOperatingHoursRequest;
import com.carwash.marketplace.api.dto.TemporaryClosureResponse;
import com.carwash.marketplace.application.BranchSchedulingService;
import com.carwash.marketplace.application.CreateTemporaryBranchClosureCommand;
import com.carwash.marketplace.application.ReplaceOperatingScheduleCommand;
import com.carwash.marketplace.application.WeeklyOperatingIntervalCommand;
import com.carwash.shared.api.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@Validated
@Tag(name = "Marketplace Branch Scheduling",
        description = "Timezone-aware weekly operating hours, temporary closures, and branch open-status decisions.")
@RequestMapping("/api/marketplace")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid scheduling request or Marketplace rule violation",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Required Marketplace permission missing",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Branch or closure not found",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "405", description = "Method not allowed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "415", description = "Unsupported media type",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class BranchSchedulingController {

    private final BranchSchedulingService scheduling;

    public BranchSchedulingController(BranchSchedulingService scheduling) {
        this.scheduling = scheduling;
    }

    @GetMapping("/branches/{branchId}/operating-hours")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Get branch operating hours",
            description = "Returns the complete recurring weekly schedule and the branch's current timezone.")
    @ApiResponse(responseCode = "200", description = "Weekly operating schedule returned")
    public BranchOperatingHoursResponse getOperatingHours(
            @PathVariable @NotBlank @Size(max = 64) String branchId
    ) {
        return MarketplaceMapper.toResponse(scheduling.getOperatingSchedule(branchId));
    }

    @PutMapping("/branches/{branchId}/operating-hours")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Replace branch operating hours",
            description = "Atomically replaces the complete weekly schedule. Intervals are half-open and may span midnight; the branch timezone is never supplied by this request.")
    @ApiResponse(responseCode = "200", description = "Weekly operating schedule replaced")
    public BranchOperatingHoursResponse replaceOperatingHours(
            @PathVariable @NotBlank @Size(max = 64) String branchId,
            @Valid @RequestBody ReplaceOperatingHoursRequest request
    ) {
        return MarketplaceMapper.toResponse(scheduling.replaceOperatingSchedule(
                branchId,
                new ReplaceOperatingScheduleCommand(request.intervals().stream()
                        .map(interval -> new WeeklyOperatingIntervalCommand(
                                interval.dayOfWeek(), interval.opensAt(), interval.closesAt()))
                        .toList())
        ));
    }

    @GetMapping("/branches/{branchId}/closures")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "List branch temporary closures",
            description = "Returns active and cancelled closure records in deterministic closure-ID order.")
    @ApiResponse(responseCode = "200", description = "Temporary closures returned")
    public List<TemporaryClosureResponse> listClosures(
            @PathVariable @NotBlank @Size(max = 64) String branchId
    ) {
        return scheduling.listTemporaryClosures(branchId).stream()
                .map(MarketplaceMapper::toResponse)
                .toList();
    }

    @PostMapping("/branches/{branchId}/closures")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create branch temporary closure",
            description = "Creates one absolute half-open closure interval. Active closures for the same branch may not overlap.")
    @ApiResponse(responseCode = "201", description = "Temporary closure created")
    public TemporaryClosureResponse createClosure(
            @PathVariable @NotBlank @Size(max = 64) String branchId,
            @Valid @RequestBody CreateTemporaryClosureRequest request
    ) {
        return MarketplaceMapper.toResponse(scheduling.createTemporaryClosure(
                branchId,
                new CreateTemporaryBranchClosureCommand(
                        request.closureId(),
                        request.startAt().toInstant(),
                        request.endAt().toInstant(),
                        request.reason())
        ));
    }

    @PostMapping("/closures/{closureId}/cancel")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Cancel branch temporary closure",
            description = "Cancels the closure without deleting its historical record.")
    @ApiResponse(responseCode = "200", description = "Temporary closure cancelled")
    public TemporaryClosureResponse cancelClosure(
            @PathVariable @NotBlank @Size(max = 64) String closureId
    ) {
        return MarketplaceMapper.toResponse(scheduling.cancelTemporaryClosure(closureId));
    }

    @GetMapping("/branches/{branchId}/open-status")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_READ')")
    @Operation(summary = "Evaluate branch open status",
            description = "Evaluates a required absolute instant using the branch's current timezone, weekly hours, active lifecycle state, and active temporary closures. Public discovery does not affect this decision.")
    @ApiResponse(responseCode = "200", description = "Branch open-status decision returned")
    public BranchOpenStatusResponse getOpenStatus(
            @PathVariable @NotBlank @Size(max = 64) String branchId,
            @Parameter(required = true,
                    description = "Required RFC 3339 timestamp with an explicit UTC offset, for example 2030-01-04T10:00:00+02:00")
            @RequestParam @NotNull OffsetDateTime at
    ) {
        return MarketplaceMapper.toResponse(scheduling.getOpenStatus(branchId, at.toInstant()));
    }
}
