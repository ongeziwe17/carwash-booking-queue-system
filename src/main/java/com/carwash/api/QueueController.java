package com.carwash.api;

import com.carwash.api.dto.CreateQueueEntryRequest;
import com.carwash.api.dto.UpdateQueuePositionRequest;
import com.carwash.domain.QueueEntry;
import com.carwash.service.QueueManagementService;
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
@Tag(name = "Queue Entries", description = "Operations for managing queue entries.")
@RequestMapping("/api/queue-entries")
public class QueueController {

    private final QueueManagementService service;

    public QueueController(QueueManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    @Operation(summary = "List all")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<QueueEntry> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAuthorization.canAccessQueueEntry(authentication, #id)")
    @Operation(summary = "Get by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public QueueEntry getById(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public QueueEntry create(@RequestBody CreateQueueEntryRequest req) {
        return service.createQueueEntry(req.toQueueEntry());
    }

    @PutMapping("/{id}/position")
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    public QueueEntry updatePosition(@PathVariable String id, @RequestBody UpdateQueuePositionRequest req) {
        return service.updatePosition(id, req.position());
    }

    @PostMapping("/{id}/call-next")
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    public QueueEntry callNext(@PathVariable String id) {
        return service.callNext(id);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    @Operation(summary = "Start queue entry")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public QueueEntry start(@PathVariable String id) {
        return service.startService(id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    @Operation(summary = "Complete queue entry")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public QueueEntry complete(@PathVariable String id) {
        return service.completeQueueEntry(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_QUEUE_OPERATE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.deleteQueueEntry(id);
    }
}
