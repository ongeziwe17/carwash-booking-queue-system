package com.carwash.catalog.api;

import com.carwash.catalog.api.dto.CreateServiceOfferingRequest;
import com.carwash.catalog.api.dto.DiscoverableServiceOfferingResponse;
import com.carwash.catalog.api.dto.ServiceOfferingResponse;
import com.carwash.catalog.api.dto.UpdateServiceOfferingRequest;
import com.carwash.catalog.application.CreateServiceOfferingCommand;
import com.carwash.catalog.application.ServiceOfferingService;
import com.carwash.catalog.application.UpdateServiceOfferingCommand;
import com.carwash.shared.api.error.ApiErrorResponse;
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
@Tag(name = "Marketplace Service Offerings",
        description = "Branch-specific service terms, configured concurrent capacity, lifecycle, and discovery.")
@RequestMapping("/api/marketplace")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid offering request or Catalog rule violation",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Required Marketplace permission missing",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Offering, branch, or service not found",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "405", description = "Method not allowed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "415", description = "Unsupported media type",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public final class ServiceOfferingController {

    private final ServiceOfferingService offerings;

    public ServiceOfferingController(ServiceOfferingService offerings) {
        this.offerings = offerings;
    }

    @GetMapping("/branches/{branchId}/offerings")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "List branch service offerings",
            description = "Returns all stored offerings for the branch, including inactive or currently ineffective records, in offering-ID order.")
    @ApiResponse(responseCode = "200", description = "Branch offerings returned")
    public List<ServiceOfferingResponse> listByBranch(
            @PathVariable @NotBlank @Size(max = 64) String branchId
    ) {
        return offerings.findOfferingsByBranch(branchId).stream()
                .map(ServiceOfferingMapper::toManagementResponse)
                .toList();
    }

    @PostMapping("/branches/{branchId}/offerings")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create branch service offering",
            description = "Creates active branch-specific price, duration, and configured concurrent capacity. Global service defaults are never copied or used as fallback values.")
    @ApiResponse(responseCode = "201", description = "Branch offering created")
    public ServiceOfferingResponse create(
            @PathVariable @NotBlank @Size(max = 64) String branchId,
            @Valid @RequestBody CreateServiceOfferingRequest request
    ) {
        return ServiceOfferingMapper.toManagementResponse(offerings.createOffering(
                branchId,
                new CreateServiceOfferingCommand(
                        request.offeringId(),
                        request.serviceId(),
                        request.price(),
                        request.estimatedDurationMin(),
                        request.concurrentCapacity())
        ));
    }

    @GetMapping("/offerings/{offeringId}")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Get branch service offering")
    @ApiResponse(responseCode = "200", description = "Branch offering returned")
    public ServiceOfferingResponse findById(
            @PathVariable @NotBlank @Size(max = 64) String offeringId
    ) {
        return ServiceOfferingMapper.toManagementResponse(offerings.findOffering(offeringId));
    }

    @PutMapping("/offerings/{offeringId}")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Update branch service offering terms",
            description = "Updates only price, estimated duration, and configured concurrent capacity while preserving offering, branch, service, and lifecycle identity.")
    @ApiResponse(responseCode = "200", description = "Branch offering updated")
    public ServiceOfferingResponse update(
            @PathVariable @NotBlank @Size(max = 64) String offeringId,
            @Valid @RequestBody UpdateServiceOfferingRequest request
    ) {
        return ServiceOfferingMapper.toManagementResponse(offerings.updateOffering(
                offeringId,
                new UpdateServiceOfferingCommand(
                        request.price(),
                        request.estimatedDurationMin(),
                        request.concurrentCapacity())
        ));
    }

    @PostMapping("/offerings/{offeringId}/activate")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Activate branch service offering",
            description = "Activates the offering record. Effective activity still requires an active global service and active branch/business chain.")
    @ApiResponse(responseCode = "200", description = "Branch offering activated")
    public ServiceOfferingResponse activate(
            @PathVariable @NotBlank @Size(max = 64) String offeringId
    ) {
        return ServiceOfferingMapper.toManagementResponse(offerings.activateOffering(offeringId));
    }

    @PostMapping("/offerings/{offeringId}/deactivate")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Deactivate branch service offering",
            description = "Deactivates the offering without deleting its branch/service relationship.")
    @ApiResponse(responseCode = "200", description = "Branch offering deactivated")
    public ServiceOfferingResponse deactivate(
            @PathVariable @NotBlank @Size(max = 64) String offeringId
    ) {
        return ServiceOfferingMapper.toManagementResponse(offerings.deactivateOffering(offeringId));
    }

    @GetMapping("/branches/{branchId}/offerings/discoverable")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_READ')")
    @Operation(summary = "List discoverable branch service offerings",
            description = "Returns customer-facing offerings only when offering, global service, branch, and business are active and branch public discovery is enabled. Operating hours and remaining capacity are not evaluated.")
    @ApiResponse(responseCode = "200", description = "Discoverable branch offerings returned")
    public List<DiscoverableServiceOfferingResponse> listDiscoverable(
            @PathVariable @NotBlank @Size(max = 64) String branchId
    ) {
        return offerings.findDiscoverableOfferingsByBranch(branchId).stream()
                .map(ServiceOfferingMapper::toDiscoveryResponse)
                .toList();
    }
}
