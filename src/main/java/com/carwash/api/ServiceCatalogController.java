package com.carwash.api;

import com.carwash.api.dto.ApiErrorResponse;
import com.carwash.api.dto.CreateServiceRequest;
import com.carwash.api.dto.UpdateServiceRequest;
import com.carwash.domain.Service;
import com.carwash.service.ServiceCatalogService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@Tag(name = "Services", description = "Operations for managing the service catalogue.")
@RequestMapping("/api/services")
public class ServiceCatalogController {

    private final ServiceCatalogService service;

    public ServiceCatalogController(ServiceCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_SERVICE_READ')")
    @Operation(summary = "List services")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Services returned"),
            @ApiResponse(responseCode = "400", description = "Invalid active parameter", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<Service> getAll(@RequestParam(required = false) Boolean active) {
        return active == null ? service.findAll() : service.findByActive(active);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SERVICE_READ')")
    @Operation(summary = "Get service by ID")
    public Service getById(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create service")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Service created"),
            @ApiResponse(responseCode = "400", description = "Invalid service request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Service create(@Valid @RequestBody CreateServiceRequest request) {
        return service.createService(request.serviceId(), request.serviceName(), request.description(), request.price(),
                request.estimatedDurationMin());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @Operation(summary = "Update service")
    public Service update(
            @PathVariable @NotBlank @Size(max = 64) String id,
            @Valid @RequestBody UpdateServiceRequest request
    ) {
        return service.updateService(id, request.serviceName(), request.description(), request.price(),
                request.estimatedDurationMin());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete service")
    public void delete(@PathVariable @NotBlank @Size(max = 64) String id) {
        service.deleteService(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @Operation(summary = "Activate service")
    public Service activate(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.activateService(id);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @Operation(summary = "Deactivate service")
    public Service deactivate(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.deactivateService(id);
    }
}
