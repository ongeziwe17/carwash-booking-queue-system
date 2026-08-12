package com.carwash.catalog.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;

import com.carwash.catalog.domain.Service;
import com.carwash.catalog.domain.ServiceRepository;

public class InMemoryServiceRepository extends InMemoryRepository<Service, String>
        implements ServiceRepository {

    @Override
    protected String getId(Service entity) {
        return entity.getServiceId();
    }
}
