package com.carwash.api;

import com.carwash.api.dto.ApiErrorResponse;
import com.carwash.api.dto.CreateQueueEntryRequest;
import com.carwash.api.dto.UpdateQueuePositionRequest;
import com.carwash.domain.QueueEntry;
import com.carwash.service.QueueManagementService;
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
@Tag(name = "Queue Entries", description = "Operations for managing queue entries.")
@RequestMapping("/api/queue-entries")
public class QueueController {

    private final QueueManagementService service;

    public QueueController(QueueManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    @Operation(summary = "List queue entries")
    public List<QueueEntry> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.canAccessQueueEntry(authentication, #id)")
    @Operation(summary = "Get queue entry by ID")
    public QueueEntry getById(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create queue entry")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Queue entry created"),
            @ApiResponse(responseCode = "400", description = "Invalid request or queue rule violation", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking or service not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public QueueEntry create(@Valid @RequestBody CreateQueueEntryRequest request) {
        return service.createQueueEntry(request.queueEntryId(), request.bookingId(), request.serviceId(), request.position());
    }

    @PutMapping("/{id}/position")
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    @Operation(summary = "Update queue position")
    public QueueEntry updatePosition(
            @PathVariable @NotBlank @Size(max = 64) String id,
            @Valid @RequestBody UpdateQueuePositionRequest request
    ) {
        return service.updatePosition(id, request.position());
    }

    @PostMapping("/{id}/call-next")
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    @Operation(summary = "Call queue entry")
    public QueueEntry callNext(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.callNext(id);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    @Operation(summary = "Start queue entry service")
    public QueueEntry start(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.startService(id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    @Operation(summary = "Complete queue entry")
    public QueueEntry complete(@PathVariable @NotBlank @Size(max = 64) String id) {
        return service.completeQueueEntry(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete queue entry")
    public void delete(@PathVariable @NotBlank @Size(max = 64) String id) {
        service.deleteQueueEntry(id);
    }
}
