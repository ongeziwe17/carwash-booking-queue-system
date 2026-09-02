package com.carwash.audit.domain;

import java.util.List;

/** Append/query-only port. Audit history cannot be updated or deleted. */
public interface AuditRepository {
    void append(AuditRecord record);
    List<AuditRecord> queryByBusinessId(String businessId, AuditQuery query);
    List<AuditRecord> queryPlatform(AuditQuery query);
}
