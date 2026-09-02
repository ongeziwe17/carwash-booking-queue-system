package com.carwash.audit.api;

import com.carwash.audit.application.*;
import com.carwash.audit.domain.AuditAction;
import com.carwash.audit.domain.AuditOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/audit-records")
@SecurityRequirement(name = "bearerAuth")
public final class AuditController {
    private final AuditQueryService service;
    private final AuditPrincipalProvider principals;

    public AuditController(AuditQueryService service, AuditPrincipalProvider principals) {
        this.service = service;
        this.principals = principals;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_AUDIT_READ')")
    @Operation(summary = "Query immutable tenant or explicitly scoped platform audit records")
    public AuditPageResponse query(
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) AuditScope scope,
            @RequestParam(required = false) String businessId
    ) {
        AuditPage page = service.query(principals.current(),
                new AuditQueryRequest(actorUserId, action, outcome, targetType, targetId, from, to,
                        cursor, limit, scope, businessId));
        return new AuditPageResponse(page.records().stream().map(AuditRecordResponse::from).toList(), page.nextCursor());
    }
}
