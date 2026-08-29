package com.carwash.notification.api;

import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.notification.domain.Notification;
import com.carwash.notification.application.NotificationManagementService;
import com.carwash.notification.api.dto.NotificationResponse;
import com.carwash.access.application.TenantAccessContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@Validated
@Tag(name = "Notifications", description = "Operations for viewing in-app notifications.")
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationManagementService service;
    private final TenantAccessContextProvider tenantAccess;

    public NotificationController(
            NotificationManagementService service,
            TenantAccessContextProvider tenantAccess
    ) {
        this.service = service;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyAuthority('PERM_NOTIFICATION_SELF_READ','PERM_BOOKING_OPERATE')")
    @Operation(
            summary = "List recent notifications for a user",
            description = "Returns the deployment-configured recent-item limit, newest first. Notifications are in-app only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications returned"),
            @ApiResponse(responseCode = "400", description = "Invalid user identifier",
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
    public List<NotificationResponse> getRecentByUserId(
            @PathVariable @NotBlank @Size(max = 64) String userId,
            @RequestParam(required = false) @Size(max = 64) String businessId
    ) {
        return service.findRecentByUserId(tenantAccess.current(), userId, businessId).stream()
                .map(this::response).toList();
    }

    private NotificationResponse response(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getUser() == null ? null : notification.getUser().getUserId(),
                notification.getBooking() == null ? null : notification.getBooking().getBookingId(),
                notification.getBranchId(),
                notification.getServiceOfferingId(),
                notification.getType(),
                notification.getMessage(),
                notification.getChannel(),
                notification.getSentAt(),
                notification.getReadAt(),
                notification.getDeliveryStatus()
        );
    }
}
