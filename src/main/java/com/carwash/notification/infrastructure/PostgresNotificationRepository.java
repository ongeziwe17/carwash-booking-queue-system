package com.carwash.notification.infrastructure;

import com.carwash.booking.domain.Booking;
import com.carwash.identity.domain.User;
import com.carwash.notification.domain.DeliveryStatus;
import com.carwash.notification.domain.Notification;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.notification.domain.NotificationCursor;
import com.carwash.notification.domain.NotificationSnapshot;
import com.carwash.shared.infrastructure.PersistenceSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository @Profile("postgres") @Transactional
public class PostgresNotificationRepository implements NotificationRepository {
    private final NotificationSpringDataRepository repository;
    public PostgresNotificationRepository(NotificationSpringDataRepository repository){this.repository=repository;}
    @Override public List<Notification> findByUserId(String id){return domains(repository.findByUserIdOrderByIdAsc(id));}
    @Override public List<Notification> findByBookingId(String id){return domains(repository.findByBookingIdOrderByIdAsc(id));}
    @Override public List<Notification> findByUserIdAndBusinessId(String userId,String businessId){return domains(repository.findByUserTenant(userId,businessId));}
    @Override public List<Notification> findByBusinessId(String businessId){return domains(repository.findByTenant(businessId));}
    @Override public Optional<Notification> findByIdAndBusinessId(String id,String businessId){return repository.findTenantScoped(id,businessId).map(this::domain);}
    @Override public List<NotificationSnapshot> findPageByUserId(String userId,boolean unreadOnly,
            NotificationCursor cursor,int limit){return snapshots(repository.findUserInbox(userId,unreadOnly,
            cursor==null?null:PersistenceSupport.databaseTime(cursor.sentAt()),
            cursor==null?0:PersistenceSupport.nanoRemainder(cursor.sentAt()),
            cursor==null?"":cursor.notificationId(),limit));}
    @Override public List<NotificationSnapshot> findPageByUserIdAndBusinessId(String userId,String businessId,
            boolean unreadOnly,NotificationCursor cursor,int limit){return snapshots(repository.findTenantInbox(
            userId,businessId,unreadOnly,cursor==null?null:PersistenceSupport.databaseTime(cursor.sentAt()),
            cursor==null?0:PersistenceSupport.nanoRemainder(cursor.sentAt()),
            cursor==null?"":cursor.notificationId(),limit));}
    @Override public long countUnreadByUserId(String userId){return repository.countUnreadByUserId(userId);}
    @Override public long countUnreadByUserIdAndBusinessId(String userId,String businessId){return repository.countTenantUnread(userId,businessId);}
    @Override public Optional<NotificationSnapshot> findSnapshotByIdAndUserId(String id,String userId){return repository.findByIdAndUserId(id,userId).map(this::snapshot);}
    @Override public Optional<NotificationSnapshot> markAsReadByUserId(String id,String userId,LocalDateTime readAt){
        repository.markRead(id,userId,PersistenceSupport.databaseTime(readAt),PersistenceSupport.nanoRemainder(readAt));
        repository.flush();
        return repository.findByIdAndUserId(id,userId).map(this::snapshot);
    }
    @Override public int markAllAsReadByUserId(String userId,LocalDateTime readAt){int count=repository.markAllRead(
            userId,PersistenceSupport.databaseTime(readAt),PersistenceSupport.nanoRemainder(readAt));repository.flush();return count;}
    @Override public int deleteByUserId(String id){int count=repository.deleteByUserId(id);repository.flush();return count;}
    @Override public int deleteByBookingId(String id){int count=repository.deleteByBookingId(id);repository.flush();return count;}
    @Override public int deleteByBookingIdAndBusinessId(String id,String businessId){int count=repository.deleteByBookingIdAndBusinessId(id,businessId);repository.flush();return count;}
    @Override public int deleteByBookingIdAndUserId(String id,String userId){int count=repository.deleteByBookingIdAndUserId(id,userId);repository.flush();return count;}
    @Override public int deleteByBookingIdForAdministrator(String id){return deleteByBookingId(id);}
    @Override public boolean insert(Notification v){if(repository.existsById(v.getNotificationId()))return false;repository.saveAndFlush(entity(v));return true;}
    @Override public boolean update(Notification v){Optional<NotificationJpaEntity> found=repository.findById(v.getNotificationId());if(found.isEmpty())return false;apply(v,found.get());repository.saveAndFlush(found.get());return true;}
    @Override public Optional<Notification> findById(String id){return repository.findById(id).map(this::domain);}
    @Override public List<Notification> findAll(){return domains(repository.findAllByOrderByIdAsc());}
    @Override public boolean deleteById(String id){Optional<NotificationJpaEntity> found=repository.findById(id);if(found.isEmpty())return false;repository.delete(found.get());repository.flush();return true;}
    @Override public boolean existsById(String id){return repository.existsById(id);}
    private List<Notification> domains(List<NotificationJpaEntity> values){return values.stream().map(this::domain).toList();}
    private List<NotificationSnapshot> snapshots(List<NotificationJpaEntity> values){return values.stream().map(this::snapshot).toList();}
    private NotificationSnapshot snapshot(NotificationJpaEntity e){return new NotificationSnapshot(e.id,e.userId,e.bookingId,
            e.branchId,e.offeringId,e.type,e.message,e.channel,PersistenceSupport.domainTime(e.sentAt,e.sentAtNano),
            PersistenceSupport.domainTime(e.readAt,e.readAtNano),DeliveryStatus.valueOf(e.status));}
    private Notification domain(NotificationJpaEntity e){User user=new User();user.setUserId(e.userId);Booking booking=null;if(e.bookingId!=null){booking=new Booking();booking.setBookingId(e.bookingId);if(e.branchId!=null&&e.offeringId!=null)booking.assignOperationalScope(e.branchId,e.offeringId);}Notification v=new Notification();v.setNotificationId(e.id);v.setUser(user);v.setBooking(booking);v.setBranchId(e.branchId);v.setServiceOfferingId(e.offeringId);v.setType(e.type);v.setMessage(e.message);v.setChannel(e.channel);v.setSentAt(PersistenceSupport.domainTime(e.sentAt,e.sentAtNano));v.setReadAt(PersistenceSupport.domainTime(e.readAt,e.readAtNano));v.setDeliveryStatus(DeliveryStatus.valueOf(e.status));return v;}
    private static NotificationJpaEntity entity(Notification v){NotificationJpaEntity e=new NotificationJpaEntity();apply(v,e);return e;}
    private static void apply(Notification v,NotificationJpaEntity e){e.id=v.getNotificationId();e.userId=v.getUser().getUserId();e.bookingId=v.getBooking()==null?null:v.getBooking().getBookingId();e.branchId=v.getBranchId();e.offeringId=v.getServiceOfferingId();e.type=v.getType();e.message=v.getMessage();e.channel=v.getChannel();e.sentAt=PersistenceSupport.databaseTime(v.getSentAt());e.sentAtNano=PersistenceSupport.nanoRemainder(v.getSentAt());e.readAt=PersistenceSupport.databaseTime(v.getReadAt());e.readAtNano=PersistenceSupport.nanoRemainder(v.getReadAt());e.status=v.getDeliveryStatus().name();}
}
