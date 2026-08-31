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
    @org.springframework.data.jpa.repository.Query(value="select o.* from service_offerings o join branches b on b.branch_id=o.branch_id where o.offering_id=:offeringId and b.business_id=:businessId",nativeQuery=true)
    Optional<ServiceOfferingJpaEntity> findTenantScoped(String offeringId, String businessId);
    @org.springframework.data.jpa.repository.Query(value="select o.* from service_offerings o join branches b on b.branch_id=o.branch_id where o.branch_id=:branchId and b.business_id=:businessId order by o.offering_id",nativeQuery=true)
    List<ServiceOfferingJpaEntity> findByBranchTenant(String branchId, String businessId);
}
