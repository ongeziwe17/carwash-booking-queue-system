package com.carwash.catalog.domain;

import com.carwash.shared.domain.Repository;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends Repository<ServiceOffering, String> {

    List<ServiceOffering> findByBranchId(String branchId);

    List<ServiceOffering> findByServiceId(String serviceId);

    Optional<ServiceOffering> findByBranchIdAndServiceId(String branchId, String serviceId);

    Optional<ServiceOffering> findByIdAndBusinessId(String offeringId, String businessId);

    List<ServiceOffering> findByBranchIdAndBusinessId(String branchId, String businessId);

    boolean existsByServiceId(String serviceId);
}
