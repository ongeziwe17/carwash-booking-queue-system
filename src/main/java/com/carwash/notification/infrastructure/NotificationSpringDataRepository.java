package com.carwash.notification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

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
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("delete from NotificationJpaEntity notification where notification.bookingId=:bookingId "
            + "and notification.userId=:userId")
    int deleteByBookingIdAndUserId(String bookingId,String userId);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query(value="delete from notifications n where n.booking_id=:bookingId and exists "
            + "(select 1 from branches b where b.branch_id=n.branch_id and b.business_id=:businessId)",
            nativeQuery=true)
    int deleteByBookingIdAndBusinessId(String bookingId,String businessId);
    @Query(value="select n.* from notifications n join branches b on b.branch_id=n.branch_id where n.user_id=:userId and b.business_id=:businessId order by n.notification_id",nativeQuery=true)
    List<NotificationJpaEntity> findByUserTenant(String userId,String businessId);
    @Query(value="select n.* from notifications n join branches b on b.branch_id=n.branch_id where b.business_id=:businessId order by n.notification_id",nativeQuery=true)
    List<NotificationJpaEntity> findByTenant(String businessId);
    @Query(value="select n.* from notifications n join branches b on b.branch_id=n.branch_id where n.notification_id=:notificationId and b.business_id=:businessId",nativeQuery=true)
    Optional<NotificationJpaEntity> findTenantScoped(String notificationId,String businessId);

    Optional<NotificationJpaEntity> findByIdAndUserId(String notificationId,String userId);

    @Query(value="""
            select n.* from notifications n
            where n.user_id=:userId
              and (:unreadOnly=false or n.delivery_status='SENT')
              and n.sent_at is not null
              and (cast(:cursorAt as timestamp) is null
                or n.sent_at < cast(:cursorAt as timestamp)
                or (n.sent_at = cast(:cursorAt as timestamp)
                  and n.sent_at_nano_remainder < :cursorNano)
                or (n.sent_at = cast(:cursorAt as timestamp)
                  and n.sent_at_nano_remainder = :cursorNano
                  and n.notification_id < :cursorId))
            order by n.sent_at desc, n.sent_at_nano_remainder desc, n.notification_id desc
            limit :limit
            """,nativeQuery=true)
    List<NotificationJpaEntity> findUserInbox(
            String userId,boolean unreadOnly,LocalDateTime cursorAt,short cursorNano,String cursorId,int limit);

    @Query(value="""
            select n.* from notifications n
            join branches b on b.branch_id=n.branch_id
            where n.user_id=:userId and b.business_id=:businessId
              and (:unreadOnly=false or n.delivery_status='SENT')
              and n.sent_at is not null
              and (cast(:cursorAt as timestamp) is null
                or n.sent_at < cast(:cursorAt as timestamp)
                or (n.sent_at = cast(:cursorAt as timestamp)
                  and n.sent_at_nano_remainder < :cursorNano)
                or (n.sent_at = cast(:cursorAt as timestamp)
                  and n.sent_at_nano_remainder = :cursorNano
                  and n.notification_id < :cursorId))
            order by n.sent_at desc, n.sent_at_nano_remainder desc, n.notification_id desc
            limit :limit
            """,nativeQuery=true)
    List<NotificationJpaEntity> findTenantInbox(
            String userId,String businessId,boolean unreadOnly,LocalDateTime cursorAt,
            short cursorNano,String cursorId,int limit);

    @Query(value="select count(*) from notifications n where n.user_id=:userId and n.delivery_status='SENT'",
            nativeQuery=true)
    long countUnreadByUserId(String userId);

    @Query(value="""
            select count(*) from notifications n
            join branches b on b.branch_id=n.branch_id
            where n.user_id=:userId and b.business_id=:businessId and n.delivery_status='SENT'
            """,nativeQuery=true)
    long countTenantUnread(String userId,String businessId);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query(value="""
            update notifications
            set delivery_status='READ', read_at=:readAt,
                read_at_nano_remainder=:readAtNano, version=version+1
            where notification_id=:notificationId and user_id=:userId and delivery_status='SENT'
            """,nativeQuery=true)
    int markRead(String notificationId,String userId,LocalDateTime readAt,short readAtNano);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query(value="""
            update notifications
            set delivery_status='READ', read_at=:readAt,
                read_at_nano_remainder=:readAtNano, version=version+1
            where user_id=:userId and delivery_status='SENT'
            """,nativeQuery=true)
    int markAllRead(String userId,LocalDateTime readAt,short readAtNano);
}
