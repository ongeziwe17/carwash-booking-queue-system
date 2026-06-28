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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
@Tag(name = "Reports", description = "Read-only operational reports.")
@RequestMapping("/api/reports")
public class ReportController {

    private final DailySummaryReportService dailySummaryReportService;

    public ReportController(DailySummaryReportService dailySummaryReportService) {
        this.dailySummaryReportService = dailySummaryReportService;
    }

    @GetMapping("/daily-summary")
    @Operation(summary = "Get daily summary report")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DailySummaryReportResponse dailySummary(@RequestParam(required = false) String date) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException("date query parameter is required in yyyy-MM-dd format");
        }

        try {
            return dailySummaryReportService.generateDailySummary(LocalDate.parse(date));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("date query parameter must use yyyy-MM-dd format");
        }
    }
}
