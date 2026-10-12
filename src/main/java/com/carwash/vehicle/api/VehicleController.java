package com.carwash.vehicle.api;

import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.vehicle.api.dto.CreateVehicleRequest;
import com.carwash.vehicle.api.dto.UpdateVehicleRequest;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.vehicle.application.VehicleManagementService;
import com.carwash.access.application.TenantAccessContextProvider;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import com.carwash.shared.api.PageResponse;

@RestController
@Validated
@Tag(name = "Vehicles", description = "Operations for managing vehicles.")
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleManagementService service;
    private final TenantAccessContextProvider tenantAccess;

    public VehicleController(VehicleManagementService service, TenantAccessContextProvider tenantAccess) {
        this.service = service;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(summary = "List all vehicles")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Invalid pagination, filter or sort",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "200", description = "Vehicles returned", useReturnTypeSchema = true),
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
        @io.swagger.v3.oas.annotations.Parameter(name = "sort", description = "Repeat up to three field,asc|desc terms from the endpoint allowlist; unique ID tie-breaker and NULLS LAST. See API-PAGINATION.md.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
        @io.swagger.v3.oas.annotations.Parameter(name = "userId", description = "Exact owner ID, intersected with mandatory authorized business.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
        @io.swagger.v3.oas.annotations.Parameter(name = "vehicleType", description = "Exact case-sensitive vehicle type; sorts id,brand,model.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    })
    public PageResponse<com.carwash.vehicle.api.dto.VehicleListResponse> getAll(
            @RequestParam(required = false) @Size(max = 64) String businessId,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam org.springframework.util.MultiValueMap<String, String> parameters
    ) {
        return PageResponse.from(service.findPage(tenantAccess.current(), businessId, com.carwash.vehicle.application.VehicleListPolicy.POLICY.parse(parameters)).map(com.carwash.vehicle.api.dto.VehicleListResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_VEHICLE_SELF_MANAGE','PERM_VEHICLE_OPERATE')")
    @Operation(summary = "Get vehicle by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle returned"),
            @ApiResponse(responseCode = "400", description = "Invalid identifier",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Vehicle getById(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.findById(tenantAccess.current(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_VEHICLE_SELF_MANAGE')")
    @Operation(summary = "Create vehicle")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vehicle created"),
            @ApiResponse(responseCode = "400", description = "Invalid request or business rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Owner not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Vehicle create(@Valid @RequestBody CreateVehicleRequest req) {
        return service.createVehicle(
                tenantAccess.current(),
                req.userId(),
                req.vehicleId(),
                req.plateNumber(),
                req.vehicleType(),
                req.brand(),
                req.model(),
                req.color(),
                req.notes()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_VEHICLE_SELF_MANAGE','PERM_VEHICLE_OPERATE')")
    @Operation(summary = "Update vehicle")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request or business rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Vehicle update(
            @PathVariable @NotBlank @Size(max = 64) String id,
            @Valid @RequestBody UpdateVehicleRequest request
    ) {
        return service.updateVehicle(
                tenantAccess.current(),
                id,
                request.plateNumber(),
                request.vehicleType(),
                request.brand(),
                request.model(),
                request.color(),
                request.notes()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_VEHICLE_SELF_MANAGE','PERM_VEHICLE_OPERATE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete vehicle")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vehicle deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid identifier or vehicle-deletion rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public void delete(@PathVariable @NotBlank @Size(max = 64) String id) {
        service.deleteVehicle(tenantAccess.current(), id);
    }
}
