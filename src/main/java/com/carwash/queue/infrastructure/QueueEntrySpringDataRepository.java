package com.carwash.queue.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

interface QueueEntrySpringDataRepository extends JpaRepository<QueueEntryJpaEntity,String> {
    String ORDER = " order by q.branchId asc, case when q.status in ('WAITING','CALLED','IN_PROGRESS') then 0 else 1 end asc, q.position asc, q.joinedAt asc, q.joinedAtNano asc, q.id asc";
    @Query("select q from QueueEntryJpaEntity q" + ORDER) List<QueueEntryJpaEntity> ordered();
    @Query("select q from QueueEntryJpaEntity q where q.status in ('WAITING','CALLED','IN_PROGRESS')" + ORDER) List<QueueEntryJpaEntity> activeOrdered();
    @Query("select q from QueueEntryJpaEntity q where q.branchId=:branchId and q.status in ('WAITING','CALLED','IN_PROGRESS')" + ORDER) List<QueueEntryJpaEntity> activeByBranch(String branchId);
    @Query("select q from QueueEntryJpaEntity q where q.status='WAITING'" + ORDER) List<QueueEntryJpaEntity> waiting();
    @Query("select q from QueueEntryJpaEntity q where q.branchId=:branchId and q.status='WAITING'" + ORDER) List<QueueEntryJpaEntity> waitingByBranch(String branchId);
    List<QueueEntryJpaEntity> findByBookingIdOrderByIdAsc(String bookingId);
    @Query("select q from QueueEntryJpaEntity q where q.serviceId=:serviceId" + ORDER) List<QueueEntryJpaEntity> byService(String serviceId);
    @Query("select q from QueueEntryJpaEntity q where q.branchId=:branchId" + ORDER) List<QueueEntryJpaEntity> byBranch(String branchId);
    boolean existsByBookingId(String bookingId);
    boolean existsByBookingIdAndStatusIn(String bookingId,List<String> statuses);
    boolean existsByServiceId(String serviceId);
    boolean existsByOfferingId(String offeringId);
    @Query(value="select q.* from queue_entries q join branches b on b.branch_id=q.branch_id where b.business_id=:businessId order by q.branch_id, case when q.queue_status in ('WAITING','CALLED','IN_PROGRESS') then 0 else 1 end, q.position, q.joined_at, q.joined_at_nano_remainder, q.queue_entry_id",nativeQuery=true)
    List<QueueEntryJpaEntity> byTenant(String businessId);
    @Query(value="select q.* from queue_entries q join branches b on b.branch_id=q.branch_id where q.branch_id=:branchId and b.business_id=:businessId order by case when q.queue_status in ('WAITING','CALLED','IN_PROGRESS') then 0 else 1 end,q.position,q.joined_at,q.joined_at_nano_remainder,q.queue_entry_id",nativeQuery=true)
    List<QueueEntryJpaEntity> byBranchTenant(String branchId,String businessId);
    @Query(value="select q.* from queue_entries q join branches b on b.branch_id=q.branch_id where q.queue_entry_id=:queueEntryId and b.business_id=:businessId",nativeQuery=true)
    Optional<QueueEntryJpaEntity> tenantScoped(String queueEntryId,String businessId);
    Optional<QueueEntryJpaEntity> findByIdAndUserId(String id,String userId);
    @Query(value="select q.* from queue_entries q join branches b on b.branch_id=q.branch_id where q.branch_id=:branchId and b.business_id=:businessId and q.queue_status='WAITING' order by q.position,q.joined_at,q.joined_at_nano_remainder,q.queue_entry_id limit 1",nativeQuery=true)
    Optional<QueueEntryJpaEntity> nextWaitingTenantScoped(String branchId,String businessId);
}
