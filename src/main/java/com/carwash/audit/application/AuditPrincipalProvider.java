package com.carwash.audit.application;

/** Port supplied by the canonical authentication capability. */
@FunctionalInterface
public interface AuditPrincipalProvider { AuditPrincipal current(); }
