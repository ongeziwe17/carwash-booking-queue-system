package com.carwash.api;

import com.carwash.api.dto.ApiErrorResponse;
import com.carwash.api.dto.CreateVehicleRequest;
import com.carwash.api.dto.UpdateVehicleRequest;
import com.carwash.domain.Vehicle;
import com.carwash.service.VehicleManagementService;
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

import java.util.List;

@RestController
@Validated
@Tag(name = "Vehicles", description = "Operations for managing vehicles.")
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleManagementService service;

    public VehicleController(VehicleManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(summary = "List all vehicles")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicles returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<Vehicle> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.canAccessVehicle(authentication, #id)")
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
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@resourceAuthorization.canCreateFor(authentication, #req.userId())")
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
    @PreAuthorize("@resourceAuthorization.canAccessVehicle(authentication, #id)")
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
    @PreAuthorize("@resourceAuthorization.canAccessVehicle(authentication, #id)")
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
        service.deleteVehicle(id);
    }
}
