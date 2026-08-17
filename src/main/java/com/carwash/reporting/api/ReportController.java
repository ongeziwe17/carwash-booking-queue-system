package com.carwash.reporting.api;

import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.reporting.api.dto.DailySummaryReportResponse;
import com.carwash.reporting.application.DailySummaryReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Validated
@Tag(name = "Reports", description = "Read-only operational reports.")
@RequestMapping("/api/reports")
public class ReportController {

    private final DailySummaryReportService dailySummaryReportService;

    public ReportController(DailySummaryReportService dailySummaryReportService) {
        this.dailySummaryReportService = dailySummaryReportService;
    }

    @GetMapping("/daily-summary")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(
            summary = "Get daily summary report",
            description = "Returns booking and queue counts for the required ISO calendar date and exactly one "
                    + "branchId or businessId scope. Booking schedule values are interpreted in their branch timezone."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Daily summary returned"),
            @ApiResponse(responseCode = "400", description = "Missing, invalid, or conflicting report scope/date",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Branch or business scope not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DailySummaryReportResponse dailySummary(
            @Parameter(description = "Required report date in yyyy-MM-dd format", example = "2026-08-09")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @Size(max = 64) String branchId,
            @RequestParam(required = false) @Size(max = 64) String businessId
    ) {
        return dailySummaryReportService.generateDailySummary(date, branchId, businessId);
    }
}
