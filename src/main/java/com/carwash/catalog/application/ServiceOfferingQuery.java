package com.carwash.catalog.application;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingQuery {

    Optional<ServiceOfferingSnapshot> findOfferingOptional(String offeringId);

    List<ServiceOfferingSnapshot> findOfferingsByBranch(String branchId);

    List<ServiceOfferingSnapshot> findDiscoverableOfferingsByBranch(String branchId);

    Optional<ServiceOfferingSnapshot> findOfferingByBranchAndService(String branchId, String serviceId);
}
