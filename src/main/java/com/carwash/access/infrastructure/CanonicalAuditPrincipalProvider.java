package com.carwash.access.infrastructure;

import com.carwash.access.application.TenantAccessContext;
import com.carwash.access.application.TenantAccessContextProvider;
import com.carwash.audit.application.AuditPrincipal;
import com.carwash.audit.application.AuditPrincipalProvider;
import org.springframework.stereotype.Component;

@Component
public final class CanonicalAuditPrincipalProvider implements AuditPrincipalProvider {
    private final TenantAccessContextProvider contexts;

    public CanonicalAuditPrincipalProvider(TenantAccessContextProvider contexts) { this.contexts = contexts; }

    @Override
    public AuditPrincipal current() {
        TenantAccessContext context = contexts.current();
        return new AuditPrincipal(context.userId(), context.role().name(), context.businessId());
    }
}
