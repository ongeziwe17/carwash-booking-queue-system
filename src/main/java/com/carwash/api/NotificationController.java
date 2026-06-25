package com.carwash.api;

import com.carwash.domain.Notification;
import com.carwash.service.NotificationManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Notifications", description = "Operations for viewing in-app notifications.")
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationManagementService service;

    public NotificationController(NotificationManagementService service) {
        this.service = service;
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "List recent notifications for a user")
    public List<Notification> getRecentByUserId(@PathVariable String userId) {
        return service.findRecentByUserId(userId);
    }
}
