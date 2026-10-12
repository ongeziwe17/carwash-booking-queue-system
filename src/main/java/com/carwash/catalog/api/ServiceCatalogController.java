package com.carwash.catalog.api;

import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.catalog.api.dto.UpdateServiceRequest;
import com.carwash.catalog.domain.Service;
import com.carwash.catalog.application.ServiceCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.carwash.shared.api.PageResponse;
import com.carwash.access.application.TenantAccessContextProvider;

@RestController
@Validated
@Tag(name = "Services", description = "Operations for managing the service catalogue.")
@RequestMapping("/api/services")
public class ServiceCatalogController {

    private final ServiceCatalogService service;
    private final TenantAccessContextProvider tenantAccess;

    public ServiceCatalogController(ServiceCatalogService service, TenantAccessContextProvider tenantAccess) {
        this.service = service;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_SERVICE_READ')")
    @Operation(summary = "List services", description = "Optionally filter the global catalogue by active state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Services returned", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Invalid active parameter",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @io.swagger.v3.oas.annotations.Parameters({
        @io.swagger.v3.oas.annotations.Parameter(name = "id", description = "Exact resource ID; narrows authorized scope only.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", maxLength = 64)),
        @io.swagger.v3.oas.annotations.Parameter(name = "page", description = "Zero-based page; default 0. Invalid or overflowing offsets return ApiErrorResponse 400.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", minimum = "0", defaultValue = "0")),
        @io.swagger.v3.oas.annotations.Parameter(name = "size", description = "Page size, default 20; rejects values outside 1-100.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")),
        @io.swagger.v3.oas.annotations.Parameter(name = "sort", description = "Repeat up to three field,asc|desc terms from the endpoint allowlist; unique ID tie-breaker and NULLS LAST. See API-PAGINATION.md.", style = io.swagger.v3.oas.annotations.enums.ParameterStyle.FORM, explode = io.swagger.v3.oas.annotations.enums.Explode.TRUE, array = @io.swagger.v3.oas.annotations.media.ArraySchema(maxItems = 3, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))),
        @io.swagger.v3.oas.annotations.Parameter(name = "name", description = "Exact case-sensitive service name", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
        @io.swagger.v3.oas.annotations.Parameter(name = "active", description = "Strict true or false; sorts id,name,price,duration.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    })
    public PageResponse<com.carwash.catalog.api.dto.ServiceListResponse> getAll(
            @RequestParam(required = false) Boolean active,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam org.springframework.util.MultiValueMap<String, String> parameters
    ) {
        return PageResponse.from(service.findPage(com.carwash.catalog.application.ServiceListPolicy.POLICY.parse(parameters)).map(com.carwash.catalog.api.dto.ServiceListResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SERVICE_READ')")
    @Operation(summary = "Get service by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service returned"),
            @ApiResponse(responseCode = "400", description = "Invalid identifier",
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
    public Service getById(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create service")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Service created"),
            @ApiResponse(responseCode = "400", description = "Invalid service request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Service create(@Valid @RequestBody CreateServiceRequest request) {
        return service.createService(
                tenantAccess.current(),
                request.serviceId(),
                request.serviceName(),
                request.description(),
                request.price(),
                request.estimatedDurationMin()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @Operation(summary = "Update service")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request or service rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Service update(
            @PathVariable @NotBlank @Size(max = 64) String id,
            @Valid @RequestBody UpdateServiceRequest request
    ) {
        return service.updateService(
                tenantAccess.current(),
                id,
                request.serviceName(),
                request.description(),
                request.price(),
                request.estimatedDurationMin()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete service")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Service deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid identifier or service-deletion rule violation",
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
    public void delete(@PathVariable @NotBlank @Size(max = 64) String id) {
        service.deleteService(tenantAccess.current(), id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @Operation(summary = "Activate service")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service activated"),
            @ApiResponse(responseCode = "400", description = "Invalid identifier or service rule violation",
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
    public Service activate(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.activateService(tenantAccess.current(), id);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @Operation(summary = "Deactivate service")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service deactivated"),
            @ApiResponse(responseCode = "400", description = "Invalid identifier or service rule violation",
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
    public Service deactivate(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.deactivateService(tenantAccess.current(), id);
    }
}
