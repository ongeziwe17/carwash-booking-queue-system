package com.carwash.api;

import com.carwash.domain.Service;
import com.carwash.service.ServiceCatalogService;
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
@Tag(name = "Services", description = "Operations for managing services.")
@RequestMapping("/api/services")
public class ServiceCatalogController {

    private final ServiceCatalogService service;

    public ServiceCatalogController(ServiceCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_SERVICE_READ')")
    @Operation(summary = "List all")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<Service> getAll(@RequestParam(required = false) Boolean active) {
        if (active == null) {
            return service.findAll();
        }
        return service.findByActive(active);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SERVICE_READ')")
    @Operation(summary = "Get by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Service getById(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Activate service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Service create(@RequestBody Service s) {
        return service.createService(s);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    public Service update(@PathVariable String id, @RequestBody Service s) {
        s.setServiceId(id);
        return service.updateService(s);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.deleteService(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @Operation(summary = "Activate service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Service activate(@PathVariable String id) {
        return service.activateService(id);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
    @Operation(summary = "Deactivate service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Service deactivate(@PathVariable String id) {
        return service.deactivateService(id);
    }
}
