package com.carwash.audit.application;

import com.carwash.audit.domain.AuditRecord;

import java.util.List;

public record AuditPage(List<AuditRecord> records, String nextCursor) { }
