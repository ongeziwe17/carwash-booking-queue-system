package com.carwash.api;

import com.carwash.api.dto.CreateVehicleRequest;
import com.carwash.domain.Vehicle;
import com.carwash.service.VehicleManagementService;
import org.springframework.http.HttpStatus;
import com.carwash.api.dto.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Vehicles", description = "Operations for managing vehicles.")
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleManagementService service;

    public VehicleController(VehicleManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(summary = "List all")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<Vehicle> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.canAccessVehicle(authentication, #id)")
    @Operation(summary = "Create")
    public Vehicle getById(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@resourceAuthorization.canCreateFor(authentication, #req.userId())")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Vehicle create(@RequestBody CreateVehicleRequest req) {
        return service.createVehicle(req.toVehicle(), req.userId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.canAccessVehicle(authentication, #id)")
    public Vehicle update(@PathVariable String id, @RequestBody Vehicle vehicle) {
        vehicle.setVehicleId(id);
        return service.updateVehicle(vehicle);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.canAccessVehicle(authentication, #id)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.deleteVehicle(id);
    }
}
