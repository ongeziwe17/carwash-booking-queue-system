package com.carwash.vehicle.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface VehicleSpringDataRepository extends JpaRepository<VehicleJpaEntity, String> {
    List<VehicleJpaEntity> findAllByOrderByIdAsc();
    List<VehicleJpaEntity> findByUserIdOrderByIdAsc(String userId);

    @Query("select (count(v) > 0) from VehicleJpaEntity v where v.userId = :userId "
            + "and lower(trim(v.plateNumber)) = lower(trim(:plateNumber)) "
            + "and (:excludedId is null or v.id <> :excludedId)")
    boolean duplicatePlate(String userId, String plateNumber, String excludedId);
    Optional<VehicleJpaEntity> findByIdAndUserId(String id,String userId);
    @Query(value="select distinct v.* from vehicles v join bookings bk on bk.vehicle_id=v.vehicle_id join branches br on br.branch_id=bk.branch_id where br.business_id=:businessId order by v.vehicle_id",nativeQuery=true)
    List<VehicleJpaEntity> findByTenant(String businessId);
    @Query(value="select v.* from vehicles v where v.vehicle_id=:vehicleId and exists (select 1 from bookings bk join branches br on br.branch_id=bk.branch_id where bk.vehicle_id=v.vehicle_id and br.business_id=:businessId)",nativeQuery=true)
    Optional<VehicleJpaEntity> findTenantScoped(String vehicleId,String businessId);
}
