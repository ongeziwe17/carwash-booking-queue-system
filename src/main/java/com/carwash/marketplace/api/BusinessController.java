package com.carwash.marketplace.api;

import com.carwash.marketplace.api.dto.BranchResponse;
import com.carwash.marketplace.api.dto.BusinessResponse;
import com.carwash.marketplace.api.dto.CreateBranchRequest;
import com.carwash.marketplace.api.dto.CreateBusinessRequest;
import com.carwash.marketplace.api.dto.UpdateBusinessRequest;
import com.carwash.marketplace.application.CreateBranchCommand;
import com.carwash.marketplace.application.MarketplaceManagementService;
import com.carwash.marketplace.application.RegisterBusinessCommand;
import com.carwash.marketplace.application.UpdateBusinessCommand;
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
@Tag(name = "Marketplace Businesses", description = "Marketplace business registration and lifecycle management.")
@RequestMapping("/api/marketplace/businesses")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request or Marketplace rule violation",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Marketplace management permission required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Business or branch not found",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "405", description = "Method not allowed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "415", description = "Unsupported media type",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class BusinessController {

    private final MarketplaceManagementService marketplace;

    public BusinessController(MarketplaceManagementService marketplace) {
        this.marketplace = marketplace;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "List Marketplace businesses",
            description = "Returns bounded business records for Marketplace management.")
    @ApiResponse(responseCode = "200", description = "Businesses returned")
    public List<BusinessResponse> findAll() {
        return marketplace.findAllBusinesses().stream().map(MarketplaceMapper::toResponse).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register Marketplace business",
            description = "Registers one independent car wash business with an active initial lifecycle state.")
    @ApiResponse(responseCode = "201", description = "Business registered")
    public BusinessResponse register(@Valid @RequestBody CreateBusinessRequest request) {
        return MarketplaceMapper.toResponse(marketplace.registerBusiness(new RegisterBusinessCommand(
                request.businessId(),
                request.businessName(),
                request.contactEmail(),
                request.contactPhone(),
                request.registrationNumber()
        )));
    }

    @GetMapping("/{businessId}")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Get Marketplace business")
    @ApiResponse(responseCode = "200", description = "Business returned")
    public BusinessResponse findById(
            @PathVariable @NotBlank @Size(max = 64) String businessId
    ) {
        return MarketplaceMapper.toResponse(marketplace.findBusiness(businessId));
    }

    @PutMapping("/{businessId}")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Update Marketplace business",
            description = "Updates contact and onboarding details while preserving business identity and lifecycle state.")
    @ApiResponse(responseCode = "200", description = "Business updated")
    public BusinessResponse update(
            @PathVariable @NotBlank @Size(max = 64) String businessId,
            @Valid @RequestBody UpdateBusinessRequest request
    ) {
        return MarketplaceMapper.toResponse(marketplace.updateBusiness(businessId, new UpdateBusinessCommand(
                request.businessName(),
                request.contactEmail(),
                request.contactPhone(),
                request.registrationNumber()
        )));
    }

    @PostMapping("/{businessId}/activate")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Activate Marketplace business",
            description = "Activates the business without recreating or deleting historical records.")
    @ApiResponse(responseCode = "200", description = "Business activated")
    public BusinessResponse activate(
            @PathVariable @NotBlank @Size(max = 64) String businessId
    ) {
        return MarketplaceMapper.toResponse(marketplace.activateBusiness(businessId));
    }

    @PostMapping("/{businessId}/deactivate")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Deactivate Marketplace business",
            description = "Deactivates the business. Its branches remain historical records but are no longer effective active or discoverable locations.")
    @ApiResponse(responseCode = "200", description = "Business deactivated")
    public BusinessResponse deactivate(
            @PathVariable @NotBlank @Size(max = 64) String businessId
    ) {
        return MarketplaceMapper.toResponse(marketplace.deactivateBusiness(businessId));
    }

    @GetMapping("/{businessId}/branches")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "List business branches",
            description = "Returns every branch owned by exactly the requested business, including inactive records.")
    @ApiResponse(responseCode = "200", description = "Branches returned")
    public List<BranchResponse> findBranches(
            @PathVariable @NotBlank @Size(max = 64) String businessId
    ) {
        return marketplace.findBranchesByBusiness(businessId).stream()
                .map(MarketplaceMapper::toResponse)
                .toList();
    }

    @PostMapping("/{businessId}/branches")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create business branch",
            description = "Creates an active physical branch owned permanently by the requested business.")
    @ApiResponse(responseCode = "201", description = "Branch created")
    public BranchResponse createBranch(
            @PathVariable @NotBlank @Size(max = 64) String businessId,
            @Valid @RequestBody CreateBranchRequest request
    ) {
        return MarketplaceMapper.toResponse(marketplace.createBranch(businessId, new CreateBranchCommand(
                request.branchId(),
                request.branchName(),
                request.addressLine1(),
                request.addressLine2(),
                request.city(),
                request.province(),
                request.postalCode(),
                request.countryCode(),
                request.latitude(),
                request.longitude(),
                request.timezone(),
                request.publicDiscoveryEnabled()
        )));
    }
}
