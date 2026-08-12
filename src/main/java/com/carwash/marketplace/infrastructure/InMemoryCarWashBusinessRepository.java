package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.CarWashBusiness;
import com.carwash.marketplace.domain.CarWashBusinessRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

public final class InMemoryCarWashBusinessRepository extends InMemoryRepository<CarWashBusiness, String>
        implements CarWashBusinessRepository {

    @Override
    protected String getId(CarWashBusiness entity) {
        return entity.getBusinessId();
    }
}
