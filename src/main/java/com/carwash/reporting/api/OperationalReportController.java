package com.carwash.reporting.api;

import com.carwash.access.application.TenantAccessContextProvider;
import com.carwash.reporting.api.dto.OperationalReportResponse;
import com.carwash.reporting.application.*;
import com.carwash.shared.api.PageResponse;
import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.shared.application.ListPolicy;
import com.carwash.shared.application.ListQuery;
import com.carwash.shared.exception.BusinessRuleViolationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

@RestController
@RequestMapping("/api/reports")
@Tag(name="Reports",description="Authenticated tenant operational dashboards; branch-local days and bounded pages.")
@ApiResponses({
    @ApiResponse(responseCode="200",description="Authorized operational aggregates"),
    @ApiResponse(responseCode="400",description="Invalid range, scope, sort or filter",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
    @ApiResponse(responseCode="401",description="Authentication required",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
    @ApiResponse(responseCode="403",description="Report permission required",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
    @ApiResponse(responseCode="404",description="Missing or foreign scope",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
})
public class OperationalReportController {
    private static final Set<String> SCOPE=Set.of("startDate","endDate","branchId","businessId");
    private final OperationalReportService service;
    private final TenantAccessContextProvider access;
    public OperationalReportController(OperationalReportService service,TenantAccessContextProvider access) {
        this.service=service;this.access=access;
    }
    private OperationalReport report(MultiValueMap<String,String> params) {
        return service.generate(access.current(),date(params,"startDate"),date(params,"endDate"),
                optional(params,"branchId"),optional(params,"businessId"));
    }
    private static String optional(MultiValueMap<String,String> params,String key) {
        if (!params.containsKey(key)) return null;
        List<String> values=params.get(key);
        if (values==null || values.size()!=1 || values.getFirst()==null || values.getFirst().isBlank())
            throw new BusinessRuleViolationException("Invalid report query");
        return values.getFirst();
    }
    private static LocalDate date(MultiValueMap<String,String> params,String key) {
        String value=optional(params,key);
        if (value==null) throw new BusinessRuleViolationException("Report date is required");
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException invalid) { throw new BusinessRuleViolationException("Invalid report date"); }
    }
    private static ListQuery query(MultiValueMap<String,String> params,ListPolicy policy) {
        return policy.parse(params);
    }
    private static void scopeOnly(MultiValueMap<String,String> params) {
        if (!SCOPE.containsAll(params.keySet())) throw new BusinessRuleViolationException("Invalid report query");
    }
    private static final String DESCRIPTION="Requires inclusive startDate/endDate (ISO dates, at most 90 days) and exactly one branchId or businessId. Owner scope is tenant bound; administrators provide explicit scope. Historical configuration may make capacity unavailable.";

    @GetMapping("/period-summary")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(summary="Operational period summary",description=DESCRIPTION)
    @ApiResponse(responseCode="200",description="Operational summary",useReturnTypeSchema=true)
    @Parameters({@Parameter(name="startDate",required=true,description="Inclusive branch-local start date"),
        @Parameter(name="endDate",required=true,description="Inclusive branch-local end date"),
        @Parameter(name="branchId",description="Explicit branch scope"),
        @Parameter(name="businessId",description="Explicit business scope")})
    public OperationalReportResponse.Summary summary(@RequestParam MultiValueMap<String,String> params) {
        scopeOnly(params);
        return OperationalReportResponse.Summary.from(report(params));
    }

    @GetMapping("/daily-breakdown")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(summary="Daily operational time series",description=DESCRIPTION+" Default date ascending; page 0, size 20, maximum 100.")
    @ApiResponse(responseCode="200",description="Bounded daily page",useReturnTypeSchema=true)
    @Parameters({@Parameter(name="startDate",required=true),@Parameter(name="endDate",required=true),
        @Parameter(name="branchId"),@Parameter(name="businessId"),
        @Parameter(name="page",schema=@Schema(type="integer",minimum="0",defaultValue="0")),
        @Parameter(name="size",schema=@Schema(type="integer",minimum="1",maximum="100",defaultValue="20")),
        @Parameter(name="sort",style=io.swagger.v3.oas.annotations.enums.ParameterStyle.FORM,
            explode=io.swagger.v3.oas.annotations.enums.Explode.TRUE,
            array=@io.swagger.v3.oas.annotations.media.ArraySchema(maxItems=3,schema=@Schema(type="string")))})
    public OperationalReportResponse.Page<OperationalReportResponse.Daily> daily(@RequestParam MultiValueMap<String,String> params) {
        var query=query(params,OperationalReportListPolicy.DAILY);var report=report(params);
        var page=query.select(report.daily(),Map.of("id",(Function<OperationalReport.Daily,?>) v->v.date().toString(),
                "date",(Function<OperationalReport.Daily,?>) OperationalReport.Daily::date,
                "bookings",(Function<OperationalReport.Daily,?>) v->v.metrics().bookingVolume()))
                .map(OperationalReportResponse.Daily::from);
        return OperationalReportResponse.Page.of(report,PageResponse.from(page));
    }
    @GetMapping("/branches")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(summary="Per-branch operational breakdown",description=DESCRIPTION+" Default booking volume descending, branch ID tie-breaker.")
    @ApiResponse(responseCode="200",description="Bounded branch page",useReturnTypeSchema=true)
    @Parameters({@Parameter(name="startDate",required=true),@Parameter(name="endDate",required=true),
        @Parameter(name="branchId"),@Parameter(name="businessId"),
        @Parameter(name="page",schema=@Schema(type="integer",minimum="0",defaultValue="0")),
        @Parameter(name="size",schema=@Schema(type="integer",minimum="1",maximum="100",defaultValue="20")),
        @Parameter(name="sort",style=io.swagger.v3.oas.annotations.enums.ParameterStyle.FORM,
            explode=io.swagger.v3.oas.annotations.enums.Explode.TRUE,
            array=@io.swagger.v3.oas.annotations.media.ArraySchema(maxItems=3,schema=@Schema(type="string")))})
    public OperationalReportResponse.Page<OperationalReportResponse.Branch> branches(@RequestParam MultiValueMap<String,String> params) {
        var query=query(params,OperationalReportListPolicy.BRANCHES);var report=report(params);
        var page=query.select(report.branches(),Map.of("id",(Function<OperationalReport.Branch,?>) OperationalReport.Branch::branchId,
                "bookings",(Function<OperationalReport.Branch,?>) v->v.metrics().bookingVolume()))
                .map(OperationalReportResponse.Branch::from);
        return OperationalReportResponse.Page.of(report,PageResponse.from(page));
    }
    @GetMapping("/offerings")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(summary="Offering popularity",description=DESCRIPTION+" Counts noncancelled scheduled bookings; optional serviceId filter.")
    @ApiResponse(responseCode="200",description="Bounded offering page",useReturnTypeSchema=true)
    @Parameters({@Parameter(name="startDate",required=true),@Parameter(name="endDate",required=true),
        @Parameter(name="branchId"),@Parameter(name="businessId"),@Parameter(name="serviceId"),
        @Parameter(name="page",schema=@Schema(type="integer",minimum="0",defaultValue="0")),
        @Parameter(name="size",schema=@Schema(type="integer",minimum="1",maximum="100",defaultValue="20")),
        @Parameter(name="sort",style=io.swagger.v3.oas.annotations.enums.ParameterStyle.FORM,
            explode=io.swagger.v3.oas.annotations.enums.Explode.TRUE,
            array=@io.swagger.v3.oas.annotations.media.ArraySchema(maxItems=3,schema=@Schema(type="string")))})
    public OperationalReportResponse.Page<OperationalReportResponse.Offering> offerings(@RequestParam MultiValueMap<String,String> params) {
        var query=query(params,OperationalReportListPolicy.OFFERINGS);var report=report(params);
        var page=query.select(report.offerings(),Map.of("id",(Function<OperationalReport.Offering,?>) OperationalReport.Offering::offeringId,
                "bookings",(Function<OperationalReport.Offering,?>) OperationalReport.Offering::bookingVolume,
                "serviceId",(Function<OperationalReport.Offering,?>) OperationalReport.Offering::serviceId))
                .map(OperationalReportResponse.Offering::from);
        return OperationalReportResponse.Page.of(report,PageResponse.from(page));
    }
    @GetMapping("/peak-hours")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(summary="Branch-local hourly periods",description=DESCRIPTION+" Hours 0-23; default booking volume descending, hour tie-breaker.")
    @ApiResponse(responseCode="200",description="Bounded hourly page",useReturnTypeSchema=true)
    @Parameters({@Parameter(name="startDate",required=true),@Parameter(name="endDate",required=true),
        @Parameter(name="branchId"),@Parameter(name="businessId"),
        @Parameter(name="page",schema=@Schema(type="integer",minimum="0",defaultValue="0")),
        @Parameter(name="size",schema=@Schema(type="integer",minimum="1",maximum="100",defaultValue="20")),
        @Parameter(name="sort",style=io.swagger.v3.oas.annotations.enums.ParameterStyle.FORM,
            explode=io.swagger.v3.oas.annotations.enums.Explode.TRUE,
            array=@io.swagger.v3.oas.annotations.media.ArraySchema(maxItems=3,schema=@Schema(type="string")))})
    public OperationalReportResponse.Page<OperationalReportResponse.PeakHour> peakHours(@RequestParam MultiValueMap<String,String> params) {
        var query=query(params,OperationalReportListPolicy.HOURS);var report=report(params);
        var page=query.select(report.peakHours(),Map.of("id",(Function<OperationalReport.PeakHour,?>) v->String.format(Locale.ROOT,"%02d",v.hour()),
                "hour",(Function<OperationalReport.PeakHour,?>) OperationalReport.PeakHour::hour,
                "bookings",(Function<OperationalReport.PeakHour,?>) OperationalReport.PeakHour::bookingVolume))
                .map(OperationalReportResponse.PeakHour::from);
        return OperationalReportResponse.Page.of(report,PageResponse.from(page));
    }
}
