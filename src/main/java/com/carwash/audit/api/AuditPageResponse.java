package com.carwash.audit.api;

import java.util.List;

public record AuditPageResponse(List<AuditRecordResponse> records, String nextCursor) { }
