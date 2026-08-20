package com.carwash.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ServiceSpringDataRepository extends JpaRepository<ServiceJpaEntity, String> {
    List<ServiceJpaEntity> findAllByOrderByIdAsc();
}

interface ServiceOfferingSpringDataRepository extends JpaRepository<ServiceOfferingJpaEntity, String> {
    List<ServiceOfferingJpaEntity> findAllByOrderByIdAsc();
    List<ServiceOfferingJpaEntity> findByBranchIdOrderByIdAsc(String branchId);
    List<ServiceOfferingJpaEntity> findByServiceIdOrderByIdAsc(String serviceId);
    Optional<ServiceOfferingJpaEntity> findByBranchIdAndServiceId(String branchId, String serviceId);
    boolean existsByServiceId(String serviceId);
}
