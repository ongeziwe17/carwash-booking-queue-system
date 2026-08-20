package com.carwash.notification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

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
}
