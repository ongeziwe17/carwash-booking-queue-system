package com.carwash.queue.infrastructure;

import com.carwash.booking.domain.Booking;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.User;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.infrastructure.PersistenceSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository @Profile("postgres") @Transactional
public class PostgresQueueEntryRepository implements QueueEntryRepository {
    private static final List<String> ACTIVE=List.of("WAITING","CALLED","IN_PROGRESS");
    private final QueueEntrySpringDataRepository repository;
    public PostgresQueueEntryRepository(QueueEntrySpringDataRepository repository){this.repository=repository;}
    @Override public List<QueueEntry> findAllOrdered(){return domains(repository.ordered());}
    @Override public List<QueueEntry> findActiveOrdered(){return domains(repository.activeOrdered());}
    @Override public List<QueueEntry> findActiveOrderedByBranch(String id){return domains(repository.activeByBranch(id));}
    @Override public Optional<QueueEntry> findNextWaiting(){return repository.waiting().stream().findFirst().map(this::domain);}
    @Override public Optional<QueueEntry> findNextWaitingByBranch(String id){return repository.waitingByBranch(id).stream().findFirst().map(this::domain);}
    @Override public List<QueueEntry> findByBookingId(String id){return domains(repository.findByBookingIdOrderByIdAsc(id));}
    @Override public Optional<String> findBookingIdById(String id){return repository.findBookingIdByQueueEntryId(id);}
    @Override public List<QueueEntry> findByServiceId(String id){return domains(repository.byService(id));}
    @Override public List<QueueEntry> findByBranchId(String id){return domains(repository.byBranch(id));}
    @Override public List<QueueEntry> findByBusinessId(String id){return domains(repository.byTenant(id));}
    @Override public List<QueueEntry> findByBranchIdAndBusinessId(String branchId,String businessId){return domains(repository.byBranchTenant(branchId,businessId));}
    @Override public Optional<QueueEntry> findByIdAndBusinessId(String id,String businessId){return repository.tenantScoped(id,businessId).map(this::domain);}
    @Override public Optional<QueueEntry> findByIdAndUserId(String id,String userId){return repository.findByIdAndUserId(id,userId).map(this::domain);}
    @Override public Optional<QueueEntry> findNextWaitingByBranchIdAndBusinessId(String branchId,String businessId){return repository.nextWaitingTenantScoped(branchId,businessId).map(this::domain);}
    @Override public boolean updateForBusiness(QueueEntry v,String businessId){
        Optional<QueueEntryJpaEntity> found=repository.tenantScoped(v.getQueueEntryId(),businessId);
        return found.isPresent()&&guardedUpdate(v,found.get().version,businessId,null);
    }
    @Override public boolean updateForUser(QueueEntry v,String userId){
        Optional<QueueEntryJpaEntity> found=repository.findByIdAndUserId(v.getQueueEntryId(),userId);
        return found.isPresent()&&guardedUpdate(v,found.get().version,null,userId);
    }
    @Override public boolean updateForAdministrator(QueueEntry v){
        Optional<QueueEntryJpaEntity> found=repository.findById(v.getQueueEntryId());
        return found.isPresent()&&guardedUpdate(v,found.get().version,null,null);
    }
    @Override public boolean deleteForBusiness(String id,String businessId){
        Optional<QueueEntryJpaEntity> found=repository.tenantScoped(id,businessId);
        return found.isPresent()&&repository.deleteBusinessScoped(id,businessId,found.get().version)==1;
    }
    @Override public boolean deleteForUser(String id,String userId){
        Optional<QueueEntryJpaEntity> found=repository.findByIdAndUserId(id,userId);
        return found.isPresent()&&repository.deleteUserScoped(id,userId,found.get().version)==1;
    }
    @Override public boolean deleteForAdministrator(String id){
        Optional<QueueEntryJpaEntity> found=repository.findById(id);
        return found.isPresent()&&repository.deleteAdministratorScoped(id,found.get().version)==1;
    }
    @Override public boolean existsByBookingId(String id){return repository.existsByBookingId(id);}
    @Override public boolean existsActiveByBookingId(String id){return repository.existsByBookingIdAndStatusIn(id,ACTIVE);}
    @Override public boolean existsByServiceId(String id){return repository.existsByServiceId(id);}
    @Override public boolean existsByServiceOfferingId(String id){return repository.existsByOfferingId(id);}
    @Override public boolean insert(QueueEntry v){if(repository.existsById(v.getQueueEntryId()))return false;try{repository.saveAndFlush(entity(v));return true;}catch(DataIntegrityViolationException failure){if(PersistenceSupport.constraint(failure,"uq_queue_active_booking"))throw new BusinessRuleViolationException("Booking already has an active queue entry");throw new BusinessRuleViolationException("Queue entry conflicts with current persistent data");}}
    @Override public boolean update(QueueEntry v){Optional<QueueEntryJpaEntity> found=repository.findById(v.getQueueEntryId());if(found.isEmpty())return false;apply(v,found.get());repository.saveAndFlush(found.get());return true;}
    @Override public Optional<QueueEntry> findById(String id){return repository.findById(id).map(this::domain);}
    @Override public List<QueueEntry> findAll(){return findAllOrdered();}
    @Override public boolean deleteById(String id){Optional<QueueEntryJpaEntity> found=repository.findById(id);if(found.isEmpty())return false;repository.delete(found.get());repository.flush();return true;}
    @Override public boolean existsById(String id){return repository.existsById(id);}
    private boolean guardedUpdate(QueueEntry v,Long version,String businessId,String userId){
        Integer activePosition=v.getQueueStatus().isActive()?v.getPosition():null;
        LocalDateTime calledAt=PersistenceSupport.databaseTime(v.getCalledAt());
        short calledAtNano=PersistenceSupport.nanoRemainder(v.getCalledAt());
        LocalDateTime startedAt=PersistenceSupport.databaseTime(v.getStartedAt());
        short startedAtNano=PersistenceSupport.nanoRemainder(v.getStartedAt());
        LocalDateTime completedAt=PersistenceSupport.databaseTime(v.getCompletedAt());
        short completedAtNano=PersistenceSupport.nanoRemainder(v.getCompletedAt());
        if(businessId!=null){
            return repository.updateBusinessScoped(
                    v.getQueueEntryId(),businessId,v.getPosition(),activePosition,v.getQueueStatus().name(),
                    calledAt,calledAtNano,startedAt,startedAtNano,completedAt,completedAtNano,
                    v.getEstimatedWaitMin(),version)==1;
        }
        if(userId!=null){
            return repository.updateUserScoped(
                    v.getQueueEntryId(),userId,v.getPosition(),activePosition,v.getQueueStatus().name(),
                    calledAt,calledAtNano,startedAt,startedAtNano,completedAt,completedAtNano,
                    v.getEstimatedWaitMin(),version)==1;
        }
        return repository.updateAdministratorScoped(
                v.getQueueEntryId(),v.getPosition(),activePosition,v.getQueueStatus().name(),
                calledAt,calledAtNano,startedAt,startedAtNano,completedAt,completedAtNano,
                v.getEstimatedWaitMin(),version)==1;
    }
    private List<QueueEntry> domains(List<QueueEntryJpaEntity> values){return values.stream().map(this::domain).toList();}
    private QueueEntry domain(QueueEntryJpaEntity e){Booking booking=new Booking();booking.setBookingId(e.bookingId);User user=new User();user.setUserId(e.userId);booking.setUser(user);Service service=new Service();service.setServiceId(e.serviceId);booking.setService(service);booking.assignOperationalScope(e.branchId,e.offeringId);QueueEntry value=new QueueEntry();value.setQueueEntryId(e.id);value.setBooking(booking);value.setService(service);value.assignOperationalScope(e.branchId,e.offeringId);value.setPosition(e.position);value.setQueueStatus(QueueStatus.valueOf(e.status));value.setJoinedAt(PersistenceSupport.domainTime(e.joinedAt,e.joinedAtNano));value.setCalledAt(PersistenceSupport.domainTime(e.calledAt,e.calledAtNano));value.setStartedAt(PersistenceSupport.domainTime(e.startedAt,e.startedAtNano));value.setCompletedAt(PersistenceSupport.domainTime(e.completedAt,e.completedAtNano));value.setEstimatedWaitMin(e.waitMinutes);return value;}
    private static QueueEntryJpaEntity entity(QueueEntry v){QueueEntryJpaEntity e=new QueueEntryJpaEntity();apply(v,e);return e;}
    private static void apply(QueueEntry v,QueueEntryJpaEntity e){e.id=v.getQueueEntryId();e.bookingId=v.getBooking().getBookingId();e.userId=v.getBooking().getUser().getUserId();e.branchId=v.getBranchId();e.offeringId=v.getServiceOfferingId();e.serviceId=v.getService().getServiceId();e.position=v.getPosition();e.status=v.getQueueStatus().name();e.activePosition=v.getQueueStatus().isActive()?v.getPosition():null;e.joinedAt=PersistenceSupport.databaseTime(v.getJoinedAt());e.joinedAtNano=PersistenceSupport.nanoRemainder(v.getJoinedAt());e.calledAt=PersistenceSupport.databaseTime(v.getCalledAt());e.calledAtNano=PersistenceSupport.nanoRemainder(v.getCalledAt());e.startedAt=PersistenceSupport.databaseTime(v.getStartedAt());e.startedAtNano=PersistenceSupport.nanoRemainder(v.getStartedAt());e.completedAt=PersistenceSupport.databaseTime(v.getCompletedAt());e.completedAtNano=PersistenceSupport.nanoRemainder(v.getCompletedAt());e.waitMinutes=v.getEstimatedWaitMin();}
}
