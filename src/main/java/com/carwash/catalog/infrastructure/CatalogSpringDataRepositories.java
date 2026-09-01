package com.carwash.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update ServiceOfferingJpaEntity o set o.price=:price,o.duration=:duration,"
            + "o.capacity=:capacity,o.status=:status,o.updatedAt=:updatedAt,"
            + "o.updatedAtNano=:updatedAtNano,o.version=o.version+1 "
            + "where o.id=:offeringId and o.version=:version and exists "
            + "(select b.id from BranchJpaEntity b where b.id=o.branchId and b.businessId=:businessId)")
    int updateBusinessScoped(String offeringId,String businessId,BigDecimal price,int duration,
                             int capacity,String status,LocalDateTime updatedAt,short updatedAtNano,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update ServiceOfferingJpaEntity o set o.price=:price,o.duration=:duration,"
            + "o.capacity=:capacity,o.status=:status,o.updatedAt=:updatedAt,"
            + "o.updatedAtNano=:updatedAtNano,o.version=o.version+1 "
            + "where o.id=:offeringId and o.version=:version")
    int updateAdministratorScoped(String offeringId,BigDecimal price,int duration,int capacity,
                                  String status,LocalDateTime updatedAt,short updatedAtNano,Long version);
}
