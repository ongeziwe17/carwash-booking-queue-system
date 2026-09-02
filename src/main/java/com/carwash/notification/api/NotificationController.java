package com.carwash.notification.api;

import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.notification.domain.NotificationSnapshot;
import com.carwash.notification.application.NotificationManagementService;
import com.carwash.notification.application.NotificationInboxPage;
import com.carwash.notification.application.MarkAllNotificationsReadResult;
import com.carwash.notification.api.dto.NotificationResponse;
import com.carwash.notification.api.dto.NotificationInboxResponse;
import com.carwash.notification.api.dto.MarkAllNotificationsReadResponse;
import com.carwash.access.application.TenantAccessContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@Validated
@Tag(name = "Notifications", description = "Tenant-aware in-app inbox and recipient read-state operations.")
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
        return service.findRecentSnapshotsByUserId(tenantAccess.current(), userId, businessId).stream()
                .map(this::response).toList();
    }

    @GetMapping("/user/{userId}/inbox")
    @PreAuthorize("hasAuthority('PERM_NOTIFICATION_SELF_READ')")
    @Operation(
            summary = "List a paginated notification inbox",
            description = "Returns newest-first keyset pages ordered by sentAt and notificationId. "
                    + "The unread count covers the complete authorized inbox. Customer scope is the authenticated "
                    + "recipient, operator scope is the authenticated tenant, and platform administrators must "
                    + "provide one exact businessId."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inbox page returned"),
            @ApiResponse(responseCode = "400", description = "Invalid cursor, identifier, or page limit",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Notification scope is not authorized",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public NotificationInboxResponse getInbox(
            @PathVariable @NotBlank @Size(max = 64) String userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) @Size(max = 512) String cursor,
            @RequestParam(required = false) @Min(1) Integer limit,
            @RequestParam(required = false) @Size(max = 64) String businessId
    ) {
        NotificationInboxPage page = service.findInbox(
                tenantAccess.current(), userId, businessId, unreadOnly, cursor, limit);
        return new NotificationInboxResponse(
                page.notifications().stream().map(this::response).toList(),
                page.unreadCount(), page.nextCursor());
    }

    @PutMapping("/{notificationId}/read")
    @PreAuthorize("hasAuthority('PERM_NOTIFICATION_SELF_READ')")
    @Operation(
            summary = "Mark one recipient notification as read",
            description = "Uses notificationId plus the authenticated recipient ID. Repeated requests are "
                    + "idempotent and retain the first authoritative readAt timestamp."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification is read"),
            @ApiResponse(responseCode = "400", description = "Invalid notification identifier or lifecycle",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Notification is missing or belongs to another recipient",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public NotificationResponse markRead(
            @PathVariable @NotBlank @Size(max = 128) String notificationId
    ) {
        return response(service.markAsRead(tenantAccess.current(), notificationId));
    }

    @PutMapping("/user/{userId}/read-all")
    @PreAuthorize("hasAuthority('PERM_NOTIFICATION_SELF_READ')")
    @Operation(
            summary = "Mark every unread recipient notification as read",
            description = "Recipient self-service only. All affected SENT records receive one application-clock "
                    + "timestamp atomically; repeated requests return an affected count of zero."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread notifications marked as read"),
            @ApiResponse(responseCode = "400", description = "Invalid user identifier",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Recipient self-service is required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public MarkAllNotificationsReadResponse markAllRead(
            @PathVariable @NotBlank @Size(max = 64) String userId
    ) {
        MarkAllNotificationsReadResult result = service.markAllAsRead(tenantAccess.current(), userId);
        return new MarkAllNotificationsReadResponse(result.affectedCount(), result.readAt());
    }

    private NotificationResponse response(NotificationSnapshot notification) {
        return new NotificationResponse(
                notification.notificationId(),
                notification.userId(),
                notification.bookingId(),
                notification.branchId(),
                notification.serviceOfferingId(),
                notification.type(),
                notification.message(),
                notification.channel(),
                notification.sentAt(),
                notification.readAt(),
                notification.deliveryStatus()
        );
    }
}
