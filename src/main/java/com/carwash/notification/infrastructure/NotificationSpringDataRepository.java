package com.carwash.notification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

interface NotificationSpringDataRepository extends JpaRepository<NotificationJpaEntity,String> {
    List<NotificationJpaEntity> findAllByOrderByIdAsc();
    List<NotificationJpaEntity> findByUserIdOrderByIdAsc(String userId);
    List<NotificationJpaEntity> findByBookingIdOrderByIdAsc(String bookingId);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("delete from NotificationJpaEntity notification where notification.userId=:userId")
    int deleteByUserId(String userId);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("delete from NotificationJpaEntity notification where notification.bookingId=:bookingId")
    int deleteByBookingId(String bookingId);
    @Query(value="select n.* from notifications n join branches b on b.branch_id=n.branch_id where n.user_id=:userId and b.business_id=:businessId order by n.notification_id",nativeQuery=true)
    List<NotificationJpaEntity> findByUserTenant(String userId,String businessId);
    @Query(value="select n.* from notifications n join branches b on b.branch_id=n.branch_id where b.business_id=:businessId order by n.notification_id",nativeQuery=true)
    List<NotificationJpaEntity> findByTenant(String businessId);
    @Query(value="select n.* from notifications n join branches b on b.branch_id=n.branch_id where n.notification_id=:notificationId and b.business_id=:businessId",nativeQuery=true)
    Optional<NotificationJpaEntity> findTenantScoped(String notificationId,String businessId);
}
