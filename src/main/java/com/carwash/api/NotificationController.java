package com.carwash.api;

import com.carwash.domain.Notification;
import com.carwash.service.NotificationManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    @Operation(summary = "List recent notifications for a user")
    public List<Notification> getRecentByUserId(
            @PathVariable @NotBlank @Size(max = 64) String userId
    ) {
        return service.findRecentByUserId(userId);
    }
}
