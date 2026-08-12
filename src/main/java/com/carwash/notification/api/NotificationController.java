package com.carwash.notification.api;

import com.carwash.shared.api.error.ApiErrorResponse;
import com.carwash.notification.domain.Notification;
import com.carwash.notification.application.NotificationManagementService;
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

import java.util.List;

@RestController
@Validated
@Tag(name = "Notifications", description = "Operations for viewing in-app notifications.")
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationManagementService service;

    public NotificationController(NotificationManagementService service) {
        this.service = service;
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("@resourceAuthorization.canAccessNotifications(authentication, #userId)")
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
    public List<Notification> getRecentByUserId(
            @PathVariable @NotBlank @Size(max = 64) String userId
    ) {
        return service.findRecentByUserId(userId);
    }
}
