package com.carwash.vehicle.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface VehicleSpringDataRepository extends JpaRepository<VehicleJpaEntity, String> {
    List<VehicleJpaEntity> findAllByOrderByIdAsc();
    List<VehicleJpaEntity> findByUserIdOrderByIdAsc(String userId);

    @Query("select (count(v) > 0) from VehicleJpaEntity v where v.userId = :userId "
            + "and lower(trim(v.plateNumber)) = lower(trim(:plateNumber)) "
            + "and (:excludedId is null or v.id <> :excludedId)")
    boolean duplicatePlate(String userId, String plateNumber, String excludedId);
}
