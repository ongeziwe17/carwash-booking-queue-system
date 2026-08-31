package com.carwash.marketplace.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface BusinessSpringDataRepository extends JpaRepository<BusinessJpaEntity,String> {
    List<BusinessJpaEntity> findAllByOrderByIdAsc();

    @Query("select (count(b) > 0) from BusinessJpaEntity b where b.registrationNumber is not null "
            + "and lower(trim(b.registrationNumber)) = lower(trim(:registrationNumber)) "
            + "and (:excludedId is null or b.id <> :excludedId)")
    boolean duplicateRegistrationNumber(String registrationNumber, String excludedId);
    @Query("select b from BusinessJpaEntity b where b.id=:businessId and b.id=:tenantId")
    java.util.Optional<BusinessJpaEntity> findTenantScoped(String businessId, String tenantId);
}
interface BranchSpringDataRepository extends JpaRepository<BranchJpaEntity,String> {
    List<BranchJpaEntity> findAllByOrderByIdAsc();
    List<BranchJpaEntity> findByBusinessIdOrderByIdAsc(String businessId);
    java.util.Optional<BranchJpaEntity> findByIdAndBusinessId(String id, String businessId);
}
interface ScheduleSpringDataRepository extends JpaRepository<ScheduleJpaEntity,String> {
    List<ScheduleJpaEntity> findAllByOrderByBranchIdAsc();
    @Query(value="select s.* from branch_operating_schedules s join branches b on b.branch_id=s.branch_id where s.branch_id=:branchId and b.business_id=:businessId",nativeQuery=true)
    java.util.Optional<ScheduleJpaEntity> findTenantScoped(String branchId,String businessId);
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
}
