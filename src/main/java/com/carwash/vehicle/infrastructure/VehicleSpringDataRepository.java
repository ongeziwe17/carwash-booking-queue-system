package com.carwash.vehicle.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update VehicleJpaEntity v set v.plateNumber=:plateNumber,v.vehicleType=:vehicleType,"
            + "v.brand=:brand,v.model=:model,v.color=:color,v.notes=:notes,v.version=v.version+1 "
            + "where v.id=:vehicleId and v.version=:version and exists "
            + "(select booking.id from BookingJpaEntity booking, BranchJpaEntity branch "
            + "where booking.vehicleId=v.id and branch.id=booking.branchId and branch.businessId=:businessId)")
    int updateBusinessScoped(String vehicleId,String businessId,String plateNumber,String vehicleType,
                             String brand,String model,String color,String notes,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update VehicleJpaEntity v set v.plateNumber=:plateNumber,v.vehicleType=:vehicleType,"
            + "v.brand=:brand,v.model=:model,v.color=:color,v.notes=:notes,v.version=v.version+1 "
            + "where v.id=:vehicleId and v.userId=:userId and v.version=:version")
    int updateUserScoped(String vehicleId,String userId,String plateNumber,String vehicleType,
                         String brand,String model,String color,String notes,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update VehicleJpaEntity v set v.plateNumber=:plateNumber,v.vehicleType=:vehicleType,"
            + "v.brand=:brand,v.model=:model,v.color=:color,v.notes=:notes,v.version=v.version+1 "
            + "where v.id=:vehicleId and v.version=:version")
    int updateAdministratorScoped(String vehicleId,String plateNumber,String vehicleType,
                                  String brand,String model,String color,String notes,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("delete from VehicleJpaEntity v where v.id=:vehicleId and v.version=:version and exists "
            + "(select booking.id from BookingJpaEntity booking, BranchJpaEntity branch "
            + "where booking.vehicleId=v.id and branch.id=booking.branchId and branch.businessId=:businessId)")
    int deleteBusinessScoped(String vehicleId,String businessId,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("delete from VehicleJpaEntity v where v.id=:vehicleId and v.userId=:userId and v.version=:version")
    int deleteUserScoped(String vehicleId,String userId,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("delete from VehicleJpaEntity v where v.id=:vehicleId and v.version=:version")
    int deleteAdministratorScoped(String vehicleId,Long version);
}
