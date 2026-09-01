package com.carwash.booking.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

interface BookingSpringDataRepository extends JpaRepository<BookingJpaEntity,String> {
    List<BookingJpaEntity> findAllByOrderByIdAsc();
    List<BookingJpaEntity> findByUserIdOrderByIdAsc(String id);
    List<BookingJpaEntity> findByVehicleIdOrderByIdAsc(String id);
    List<BookingJpaEntity> findByServiceIdOrderByIdAsc(String id);
    List<BookingJpaEntity> findByBranchIdOrderByIdAsc(String id);
    List<BookingJpaEntity> findByOfferingIdOrderByIdAsc(String id);
    List<BookingJpaEntity> findByScheduledAtAndScheduledAtNanoOrderByIdAsc(LocalDateTime time, short nano);
    boolean existsByUserId(String id);
    boolean existsByVehicleId(String id);
    boolean existsByServiceId(String id);
    boolean existsByOfferingId(String id);
    Optional<BookingJpaEntity> findByIdAndUserId(String id, String userId);
    @org.springframework.data.jpa.repository.Query(value="select bk.* from bookings bk join branches br on br.branch_id=bk.branch_id where bk.booking_id=:bookingId and br.business_id=:businessId",nativeQuery=true)
    Optional<BookingJpaEntity> findTenantScoped(String bookingId, String businessId);
    @org.springframework.data.jpa.repository.Query(value="select bk.* from bookings bk join branches br on br.branch_id=bk.branch_id where br.business_id=:businessId order by bk.booking_id",nativeQuery=true)
    List<BookingJpaEntity> findByTenant(String businessId);
    @org.springframework.data.jpa.repository.Query(value="select bk.* from bookings bk join branches br on br.branch_id=bk.branch_id where bk.branch_id=:branchId and br.business_id=:businessId order by bk.booking_id",nativeQuery=true)
    List<BookingJpaEntity> findByBranchTenant(String branchId, String businessId);
    @org.springframework.data.jpa.repository.Query(value="select bk.* from bookings bk join branches br on br.branch_id=bk.branch_id where bk.user_id=:userId and br.business_id=:businessId order by bk.booking_id",nativeQuery=true)
    List<BookingJpaEntity> findByUserTenant(String userId, String businessId);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update BookingJpaEntity b set b.vehicleId=:vehicleId,b.offeringId=:offeringId,"
            + "b.serviceId=:serviceId,b.scheduledAt=:scheduledAt,b.scheduledAtNano=:scheduledAtNano,"
            + "b.status=:status,b.specialRequest=:specialRequest,b.queueEntryId=:queueEntryId,"
            + "b.version=b.version+1 where b.id=:bookingId and b.version=:version and exists "
            + "(select branch.id from BranchJpaEntity branch where branch.id=b.branchId "
            + "and branch.businessId=:businessId)")
    int updateBusinessScoped(String bookingId,String businessId,String vehicleId,String offeringId,
                             String serviceId,LocalDateTime scheduledAt,short scheduledAtNano,
                             String status,String specialRequest,String queueEntryId,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update BookingJpaEntity b set b.vehicleId=:vehicleId,b.offeringId=:offeringId,"
            + "b.serviceId=:serviceId,b.scheduledAt=:scheduledAt,b.scheduledAtNano=:scheduledAtNano,"
            + "b.status=:status,b.specialRequest=:specialRequest,b.queueEntryId=:queueEntryId,"
            + "b.version=b.version+1 where b.id=:bookingId and b.userId=:userId and b.version=:version")
    int updateUserScoped(String bookingId,String userId,String vehicleId,String offeringId,
                         String serviceId,LocalDateTime scheduledAt,short scheduledAtNano,
                         String status,String specialRequest,String queueEntryId,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update BookingJpaEntity b set b.vehicleId=:vehicleId,b.offeringId=:offeringId,"
            + "b.serviceId=:serviceId,b.scheduledAt=:scheduledAt,b.scheduledAtNano=:scheduledAtNano,"
            + "b.status=:status,b.specialRequest=:specialRequest,b.queueEntryId=:queueEntryId,"
            + "b.version=b.version+1 where b.id=:bookingId and b.version=:version")
    int updateAdministratorScoped(String bookingId,String vehicleId,String offeringId,String serviceId,
                                  LocalDateTime scheduledAt,short scheduledAtNano,String status,
                                  String specialRequest,String queueEntryId,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("delete from BookingJpaEntity b where b.id=:bookingId and b.version=:version and exists "
            + "(select branch.id from BranchJpaEntity branch where branch.id=b.branchId "
            + "and branch.businessId=:businessId)")
    int deleteBusinessScoped(String bookingId,String businessId,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("delete from BookingJpaEntity b where b.id=:bookingId and b.userId=:userId and b.version=:version")
    int deleteUserScoped(String bookingId,String userId,Long version);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("delete from BookingJpaEntity b where b.id=:bookingId and b.version=:version")
    int deleteAdministratorScoped(String bookingId,Long version);
}
