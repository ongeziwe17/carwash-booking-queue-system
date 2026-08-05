package com.carwash.api;

import com.carwash.api.dto.ApiErrorResponse;
import com.carwash.api.dto.DailySummaryReportResponse;
import com.carwash.service.DailySummaryReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    @Operation(summary = "Get daily summary report")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Daily summary returned"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid ISO date", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DailySummaryReportResponse dailySummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return dailySummaryReportService.generateDailySummary(date);
    }
}
