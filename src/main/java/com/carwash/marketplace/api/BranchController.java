package com.carwash.marketplace.api;

import com.carwash.marketplace.api.dto.BranchResponse;
import com.carwash.marketplace.api.dto.DiscoverableBranchResponse;
import com.carwash.marketplace.api.dto.UpdateBranchRequest;
import com.carwash.marketplace.application.MarketplaceManagementService;
import com.carwash.marketplace.application.UpdateBranchCommand;
import com.carwash.shared.api.error.ApiErrorResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@Tag(name = "Marketplace Branches", description = "Marketplace branch lookup and lifecycle management.")
@RequestMapping("/api/marketplace/branches")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request or Marketplace rule violation",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Required Marketplace permission missing",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Branch not found",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "405", description = "Method not allowed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "415", description = "Unsupported media type",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class BranchController {

    private final MarketplaceManagementService marketplace;
    private final TenantAccessContextProvider tenantAccess;

    public BranchController(MarketplaceManagementService marketplace, TenantAccessContextProvider tenantAccess) {
        this.marketplace = marketplace;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping("/discoverable")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_READ')")
    @Operation(summary = "List discoverable Marketplace branches",
            description = "Returns only public-discovery-enabled branches whose branch and owning business are both active. No distance ranking is performed.")
    @ApiResponse(responseCode = "200", description = "Discoverable branches returned")
    public List<DiscoverableBranchResponse> findDiscoverable() {
        return marketplace.findDiscoverableBranches().stream()
                .map(MarketplaceMapper::toDiscoverableResponse)
                .toList();
    }

    @GetMapping("/{branchId}")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Get Marketplace branch")
    @ApiResponse(responseCode = "200", description = "Branch returned")
    public BranchResponse findById(
            @PathVariable @NotBlank @Size(max = 64) String branchId
    ) {
        return MarketplaceMapper.toResponse(marketplace.findBranch(tenantAccess.current(), branchId));
    }

    @PutMapping("/{branchId}")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Update Marketplace branch",
            description = "Updates location and discovery details while preserving branch identity and business ownership.")
    @ApiResponse(responseCode = "200", description = "Branch updated")
    public BranchResponse update(
            @PathVariable @NotBlank @Size(max = 64) String branchId,
            @Valid @RequestBody UpdateBranchRequest request
    ) {
        return MarketplaceMapper.toResponse(marketplace.updateBranch(tenantAccess.current(), branchId, new UpdateBranchCommand(
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

    @PostMapping("/{branchId}/activate")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Activate Marketplace branch",
            description = "Activates the branch record. Effective activity still requires an active owning business.")
    @ApiResponse(responseCode = "200", description = "Branch activated")
    public BranchResponse activate(
            @PathVariable @NotBlank @Size(max = 64) String branchId
    ) {
        return MarketplaceMapper.toResponse(marketplace.activateBranch(tenantAccess.current(), branchId));
    }

    @PostMapping("/{branchId}/deactivate")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_MANAGE')")
    @Operation(summary = "Deactivate Marketplace branch",
            description = "Deactivates the branch without deleting its identity or historical record.")
    @ApiResponse(responseCode = "200", description = "Branch deactivated")
    public BranchResponse deactivate(
            @PathVariable @NotBlank @Size(max = 64) String branchId
    ) {
        return MarketplaceMapper.toResponse(marketplace.deactivateBranch(tenantAccess.current(), branchId));
    }
}
