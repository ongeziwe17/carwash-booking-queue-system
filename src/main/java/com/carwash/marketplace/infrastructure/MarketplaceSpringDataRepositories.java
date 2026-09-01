package com.carwash.marketplace.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

interface BusinessSpringDataRepository extends JpaRepository<BusinessJpaEntity,String> {
    List<BusinessJpaEntity> findAllByOrderByIdAsc();

    @Query("select (count(b) > 0) from BusinessJpaEntity b where b.registrationNumber is not null "
            + "and lower(trim(b.registrationNumber)) = lower(trim(:registrationNumber)) "
            + "and (:excludedId is null or b.id <> :excludedId)")
    boolean duplicateRegistrationNumber(String registrationNumber, String excludedId);
    @Query("select b from BusinessJpaEntity b where b.id=:businessId and b.id=:tenantId")
    java.util.Optional<BusinessJpaEntity> findTenantScoped(String businessId, String tenantId);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update BusinessJpaEntity b set b.name=:name,b.email=:email,b.phone=:phone,"
            + "b.registrationNumber=:registrationNumber,b.status=:status,b.updatedAt=:updatedAt,"
            + "b.updatedAtNano=:updatedAtNano,b.version=b.version+1 "
            + "where b.id=:businessId and b.id=:tenantId and b.version=:version")
    int updateTenantScoped(String businessId,String tenantId,String name,String email,String phone,
                           String registrationNumber,String status,LocalDateTime updatedAt,
                           short updatedAtNano,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update BusinessJpaEntity b set b.name=:name,b.email=:email,b.phone=:phone,"
            + "b.registrationNumber=:registrationNumber,b.status=:status,b.updatedAt=:updatedAt,"
            + "b.updatedAtNano=:updatedAtNano,b.version=b.version+1 "
            + "where b.id=:businessId and b.version=:version")
    int updateAdministratorScoped(String businessId,String name,String email,String phone,
                                  String registrationNumber,String status,LocalDateTime updatedAt,
                                  short updatedAtNano,Long version);
}
interface BranchSpringDataRepository extends JpaRepository<BranchJpaEntity,String> {
    List<BranchJpaEntity> findAllByOrderByIdAsc();
    List<BranchJpaEntity> findByBusinessIdOrderByIdAsc(String businessId);
    java.util.Optional<BranchJpaEntity> findByIdAndBusinessId(String id, String businessId);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update BranchJpaEntity b set b.name=:name,b.address1=:address1,b.address2=:address2,"
            + "b.city=:city,b.province=:province,b.postalCode=:postalCode,b.countryCode=:countryCode,"
            + "b.latitude=:latitude,b.longitude=:longitude,b.timezone=:timezone,b.status=:status,"
            + "b.publicDiscovery=:publicDiscovery,b.updatedAt=:updatedAt,b.updatedAtNano=:updatedAtNano,"
            + "b.version=b.version+1 where b.id=:branchId and b.businessId=:businessId and b.version=:version")
    int updateBusinessScoped(String branchId,String businessId,String name,String address1,String address2,
                             String city,String province,String postalCode,String countryCode,
                             BigDecimal latitude,BigDecimal longitude,String timezone,String status,
                             boolean publicDiscovery,LocalDateTime updatedAt,short updatedAtNano,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update BranchJpaEntity b set b.name=:name,b.address1=:address1,b.address2=:address2,"
            + "b.city=:city,b.province=:province,b.postalCode=:postalCode,b.countryCode=:countryCode,"
            + "b.latitude=:latitude,b.longitude=:longitude,b.timezone=:timezone,b.status=:status,"
            + "b.publicDiscovery=:publicDiscovery,b.updatedAt=:updatedAt,b.updatedAtNano=:updatedAtNano,"
            + "b.version=b.version+1 where b.id=:branchId and b.version=:version")
    int updateAdministratorScoped(String branchId,String name,String address1,String address2,
                                  String city,String province,String postalCode,String countryCode,
                                  BigDecimal latitude,BigDecimal longitude,String timezone,String status,
                                  boolean publicDiscovery,LocalDateTime updatedAt,short updatedAtNano,Long version);
}
interface ScheduleSpringDataRepository extends JpaRepository<ScheduleJpaEntity,String> {
    List<ScheduleJpaEntity> findAllByOrderByBranchIdAsc();
    @Query(value="select s.* from branch_operating_schedules s join branches b on b.branch_id=s.branch_id where s.branch_id=:branchId and b.business_id=:businessId",nativeQuery=true)
    java.util.Optional<ScheduleJpaEntity> findTenantScoped(String branchId,String businessId);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update ScheduleJpaEntity s set s.updatedAt=:updatedAt,s.updatedAtNano=:updatedAtNano,"
            + "s.version=s.version+1 where s.branchId=:branchId and s.version=:version and exists "
            + "(select b.id from BranchJpaEntity b where b.id=s.branchId and b.businessId=:businessId)")
    int updateBusinessScoped(String branchId,String businessId,LocalDateTime updatedAt,
                             short updatedAtNano,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update ScheduleJpaEntity s set s.updatedAt=:updatedAt,s.updatedAtNano=:updatedAtNano,"
            + "s.version=s.version+1 where s.branchId=:branchId and s.version=:version")
    int updateAdministratorScoped(String branchId,LocalDateTime updatedAt,short updatedAtNano,Long version);
}
interface WeeklyIntervalSpringDataRepository extends JpaRepository<WeeklyIntervalJpaEntity,WeeklyIntervalId> {
    List<WeeklyIntervalJpaEntity> findByBranchIdOrderByOrderAsc(String branchId);
    List<WeeklyIntervalJpaEntity> findByBranchIdInOrderByBranchIdAscOrderAsc(List<String> branchIds);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("delete from WeeklyIntervalJpaEntity interval where interval.branchId=:branchId")
    int deleteByBranchId(String branchId);
}
interface ClosureSpringDataRepository extends JpaRepository<ClosureJpaEntity,String> {
    List<ClosureJpaEntity> findAllByOrderByIdAsc();
    List<ClosureJpaEntity> findByBranchIdOrderByIdAsc(String branchId);
    @Query(value="select c.* from temporary_branch_closures c join branches b on b.branch_id=c.branch_id where c.branch_id=:branchId and b.business_id=:businessId order by c.closure_id",nativeQuery=true)
    List<ClosureJpaEntity> findByBranchTenant(String branchId,String businessId);
    @Query(value="select c.* from temporary_branch_closures c join branches b on b.branch_id=c.branch_id where c.closure_id=:closureId and b.business_id=:businessId",nativeQuery=true)
    java.util.Optional<ClosureJpaEntity> findTenantScoped(String closureId,String businessId);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update ClosureJpaEntity c set c.status=:status,c.updatedAt=:updatedAt,"
            + "c.updatedAtNano=:updatedAtNano,c.version=c.version+1 "
            + "where c.id=:closureId and c.version=:version and exists "
            + "(select b.id from BranchJpaEntity b where b.id=c.branchId and b.businessId=:businessId)")
    int updateBusinessScoped(String closureId,String businessId,String status,
                             LocalDateTime updatedAt,short updatedAtNano,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update ClosureJpaEntity c set c.status=:status,c.updatedAt=:updatedAt,"
            + "c.updatedAtNano=:updatedAtNano,c.version=c.version+1 "
            + "where c.id=:closureId and c.version=:version")
    int updateAdministratorScoped(String closureId,String status,LocalDateTime updatedAt,
                                  short updatedAtNano,Long version);
}
