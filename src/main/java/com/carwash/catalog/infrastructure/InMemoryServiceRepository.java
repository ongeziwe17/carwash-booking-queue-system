package com.carwash.catalog.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;

import com.carwash.catalog.domain.Service;
import com.carwash.catalog.domain.ServiceRepository;

public class InMemoryServiceRepository extends InMemoryRepository<Service, String>
        implements ServiceRepository {
    @Override public com.carwash.shared.application.PageResult<Service> findPage(com.carwash.shared.application.ListQuery query) {
        var scope = query.scope();
        java.util.List<Service> source = findMatching(v -> true);
        return query.select(source, java.util.Map.ofEntries(java.util.Map.entry("id", (java.util.function.Function<Service, Object>) v -> v.getServiceId()),
                java.util.Map.entry("name", (java.util.function.Function<Service, Object>) v -> v.getServiceName()),
                java.util.Map.entry("price", (java.util.function.Function<Service, Object>) v -> v.getPrice()),
                java.util.Map.entry("duration", (java.util.function.Function<Service, Object>) v -> v.getEstimatedDurationMin()),
                java.util.Map.entry("active", (java.util.function.Function<Service, Object>) v -> v.isActive())));
    }


    @Override
    protected String getId(Service entity) {
        return entity.getServiceId();
    }
}
