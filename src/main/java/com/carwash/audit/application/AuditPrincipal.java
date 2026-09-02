package com.carwash.audit.application;

public record AuditPrincipal(String userId, String role, String businessId) { }
