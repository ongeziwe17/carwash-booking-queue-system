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
}
interface BranchSpringDataRepository extends JpaRepository<BranchJpaEntity,String> { List<BranchJpaEntity> findAllByOrderByIdAsc(); List<BranchJpaEntity> findByBusinessIdOrderByIdAsc(String businessId); }
interface ScheduleSpringDataRepository extends JpaRepository<ScheduleJpaEntity,String> { List<ScheduleJpaEntity> findAllByOrderByBranchIdAsc(); }
interface WeeklyIntervalSpringDataRepository extends JpaRepository<WeeklyIntervalJpaEntity,WeeklyIntervalId> {
    List<WeeklyIntervalJpaEntity> findByBranchIdOrderByOrderAsc(String branchId);
    List<WeeklyIntervalJpaEntity> findByBranchIdInOrderByBranchIdAscOrderAsc(List<String> branchIds);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("delete from WeeklyIntervalJpaEntity interval where interval.branchId=:branchId")
    int deleteByBranchId(String branchId);
}
interface ClosureSpringDataRepository extends JpaRepository<ClosureJpaEntity,String> { List<ClosureJpaEntity> findAllByOrderByIdAsc(); List<ClosureJpaEntity> findByBranchIdOrderByIdAsc(String branchId); }
