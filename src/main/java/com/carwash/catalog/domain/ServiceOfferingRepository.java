package com.carwash.catalog.domain;

import com.carwash.shared.domain.Repository;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends Repository<ServiceOffering, String> {

    List<ServiceOffering> findByBranchId(String branchId);

    Optional<ServiceOffering> findByBranchIdAndServiceId(String branchId, String serviceId);

    boolean existsByServiceId(String serviceId);
}
