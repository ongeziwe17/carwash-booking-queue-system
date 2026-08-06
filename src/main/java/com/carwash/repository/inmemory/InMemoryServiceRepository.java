package com.carwash.repository.inmemory;

import com.carwash.domain.Service;
import com.carwash.repository.ServiceRepository;

public class InMemoryServiceRepository extends InMemoryRepository<Service, String>
        implements ServiceRepository {

    @Override
    protected String getId(Service entity) {
        return entity.getServiceId();
    }
}
