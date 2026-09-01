package com.carwash.booking.infrastructure;

import com.carwash.booking.domain.Booking;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.AccountStatus;
import com.carwash.identity.domain.RoleCatalog;
import com.carwash.identity.domain.RoleName;
import com.carwash.identity.domain.User;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.infrastructure.PersistenceSupport;
import com.carwash.vehicle.domain.Vehicle;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository @Profile("postgres") @Transactional
public class PostgresBookingRepository implements BookingRepository {
    private final BookingSpringDataRepository repository;
    private final JdbcTemplate jdbc;
    public PostgresBookingRepository(BookingSpringDataRepository repository,JdbcTemplate jdbc){this.repository=repository;this.jdbc=jdbc;}

    @Override public List<Booking> findByUserId(String id){return domains(repository.findByUserIdOrderByIdAsc(id));}
    @Override public List<Booking> findByVehicleId(String id){return domains(repository.findByVehicleIdOrderByIdAsc(id));}
    @Override public List<Booking> findByServiceId(String id){return domains(repository.findByServiceIdOrderByIdAsc(id));}
    @Override public List<Booking> findByBranchId(String id){return domains(repository.findByBranchIdOrderByIdAsc(id));}
    @Override public List<Booking> findByServiceOfferingId(String id){return domains(repository.findByOfferingIdOrderByIdAsc(id));}
    @Override public List<Booking> findByIds(Collection<String> ids){if(ids==null||ids.isEmpty())return List.of();return domains(repository.findAllById(ids).stream().sorted(java.util.Comparator.comparing(entity->entity.id)).toList());}
    @Override public List<Booking> findByScheduledDateTime(LocalDateTime time){return domains(repository.findByScheduledAtAndScheduledAtNanoOrderByIdAsc(PersistenceSupport.databaseTime(time),PersistenceSupport.nanoRemainder(time)));}
    @Override public List<Booking> findByBusinessId(String id){return domains(repository.findByTenant(id));}
    @Override public List<Booking> findByBranchIdAndBusinessId(String branchId,String businessId){return domains(repository.findByBranchTenant(branchId,businessId));}
    @Override public List<Booking> findByUserIdAndBusinessId(String userId,String businessId){return domains(repository.findByUserTenant(userId,businessId));}
    @Override public Optional<Booking> findByIdAndBusinessId(String id,String businessId){return repository.findTenantScoped(id,businessId).map(entity->domain(entity,loadUsers(List.of(entity.userId)),loadVehicles(List.of(entity.vehicleId)),loadServices(List.of(entity.serviceId))));}
    @Override public Optional<Booking> findByIdAndUserId(String id,String userId){return repository.findByIdAndUserId(id,userId).map(entity->domain(entity,loadUsers(List.of(entity.userId)),loadVehicles(List.of(entity.vehicleId)),loadServices(List.of(entity.serviceId))));}
    @Override public boolean updateForBusiness(Booking value,String businessId){
        Optional<BookingJpaEntity> found=repository.findTenantScoped(value.getBookingId(),businessId);
        return found.isPresent()&&guardedUpdate(value,found.get().version,businessId,null);
    }
    @Override public boolean updateForUser(Booking value,String userId){
        Optional<BookingJpaEntity> found=repository.findByIdAndUserId(value.getBookingId(),userId);
        return found.isPresent()&&guardedUpdate(value,found.get().version,null,userId);
    }
    @Override public boolean updateForAdministrator(Booking value){
        Optional<BookingJpaEntity> found=repository.findById(value.getBookingId());
        return found.isPresent()&&guardedUpdate(value,found.get().version,null,null);
    }
    @Override public boolean deleteForBusiness(String id,String businessId){
        Optional<BookingJpaEntity> found=repository.findTenantScoped(id,businessId);
        return found.isPresent()&&repository.deleteBusinessScoped(id,businessId,found.get().version)==1;
    }
    @Override public boolean deleteForUser(String id,String userId){
        Optional<BookingJpaEntity> found=repository.findByIdAndUserId(id,userId);
        return found.isPresent()&&repository.deleteUserScoped(id,userId,found.get().version)==1;
    }
    @Override public boolean deleteForAdministrator(String id){
        Optional<BookingJpaEntity> found=repository.findById(id);
        return found.isPresent()&&repository.deleteAdministratorScoped(id,found.get().version)==1;
    }
    @Override public boolean existsByUserId(String id){return repository.existsByUserId(id);}
    @Override public boolean existsByVehicleId(String id){return repository.existsByVehicleId(id);}
    @Override public boolean existsByServiceId(String id){return repository.existsByServiceId(id);}
    @Override public boolean existsByServiceOfferingId(String id){return repository.existsByOfferingId(id);}
    @Override public boolean insert(Booking value){if(repository.existsById(value.getBookingId()))return false;try{repository.saveAndFlush(entity(value));return true;}catch(DataIntegrityViolationException failure){throw new BusinessRuleViolationException("Booking conflicts with current persistent data");}}
    @Override public boolean update(Booking value){Optional<BookingJpaEntity> found=repository.findById(value.getBookingId());if(found.isEmpty())return false;apply(value,found.get());repository.saveAndFlush(found.get());return true;}
    @Override public Optional<Booking> findById(String id){return repository.findById(id).map(entity->domain(entity,loadUsers(List.of(entity.userId)),loadVehicles(List.of(entity.vehicleId)),loadServices(List.of(entity.serviceId))));}
    @Override public List<Booking> findAll(){return domains(repository.findAllByOrderByIdAsc());}
    @Override public boolean deleteById(String id){Optional<BookingJpaEntity> found=repository.findById(id);if(found.isEmpty())return false;repository.delete(found.get());repository.flush();return true;}
    @Override public boolean existsById(String id){return repository.existsById(id);}

    private boolean guardedUpdate(Booking value,Long version,String businessId,String userId){
        String queueEntryId=value.getQueueEntry()==null?null:value.getQueueEntry().getQueueEntryId();
        LocalDateTime scheduledAt=PersistenceSupport.databaseTime(value.getScheduledDateTime());
        short scheduledAtNano=PersistenceSupport.nanoRemainder(value.getScheduledDateTime());
        if(businessId!=null){
            return repository.updateBusinessScoped(
                    value.getBookingId(),businessId,value.getVehicle().getVehicleId(),
                    value.getServiceOfferingId(),value.getService().getServiceId(),scheduledAt,scheduledAtNano,
                    value.getStatus().name(),value.getSpecialRequest(),queueEntryId,version)==1;
        }
        if(userId!=null){
            return repository.updateUserScoped(
                    value.getBookingId(),userId,value.getVehicle().getVehicleId(),
                    value.getServiceOfferingId(),value.getService().getServiceId(),scheduledAt,scheduledAtNano,
                    value.getStatus().name(),value.getSpecialRequest(),queueEntryId,version)==1;
        }
        return repository.updateAdministratorScoped(
                value.getBookingId(),value.getVehicle().getVehicleId(),value.getServiceOfferingId(),
                value.getService().getServiceId(),scheduledAt,scheduledAtNano,value.getStatus().name(),
                value.getSpecialRequest(),queueEntryId,version)==1;
    }

    private List<Booking> domains(List<BookingJpaEntity> entities){if(entities.isEmpty())return List.of();Map<String,User> users=loadUsers(entities.stream().map(e->e.userId).distinct().toList());Map<String,Vehicle> vehicles=loadVehicles(entities.stream().map(e->e.vehicleId).distinct().toList());Map<String,Service> services=loadServices(entities.stream().map(e->e.serviceId).distinct().toList());return entities.stream().map(e->domain(e,users,vehicles,services)).toList();}
    private Booking domain(BookingJpaEntity e,Map<String,User> users,Map<String,Vehicle> vehicles,Map<String,Service> services){Booking value=new Booking(e.id,users.get(e.userId),vehicles.get(e.vehicleId),e.branchId,e.offeringId,services.get(e.serviceId),PersistenceSupport.domainTime(e.scheduledAt,e.scheduledAtNano),e.specialRequest);value.setStatus(BookingStatus.valueOf(e.status));value.setCreatedAt(PersistenceSupport.domainTime(e.createdAt,e.createdAtNano));if(e.queueEntryId!=null){QueueEntry queue=new QueueEntry();queue.setQueueEntryId(e.queueEntryId);value.attachQueueEntry(queue);}return value;}
    private static BookingJpaEntity entity(Booking value){BookingJpaEntity e=new BookingJpaEntity();apply(value,e);return e;}
    private static void apply(Booking v,BookingJpaEntity e){e.id=v.getBookingId();e.userId=v.getUser().getUserId();e.vehicleId=v.getVehicle().getVehicleId();e.branchId=v.getBranchId();e.offeringId=v.getServiceOfferingId();e.serviceId=v.getService().getServiceId();e.scheduledAt=PersistenceSupport.databaseTime(v.getScheduledDateTime());e.scheduledAtNano=PersistenceSupport.nanoRemainder(v.getScheduledDateTime());e.status=v.getStatus().name();e.createdAt=PersistenceSupport.databaseTime(v.getCreatedAt());e.createdAtNano=PersistenceSupport.nanoRemainder(v.getCreatedAt());e.specialRequest=v.getSpecialRequest();e.queueEntryId=v.getQueueEntry()==null?null:v.getQueueEntry().getQueueEntryId();}

    private Map<String,User> loadUsers(List<String> ids){Map<String,User> values=new HashMap<>();if(ids.isEmpty())return values;String sql="select u.user_id,u.full_name,u.email,u.phone,u.account_status,u.created_at,u.created_at_nano_remainder,u.last_login_at,u.last_login_at_nano_remainder,r.role_name from users u join user_role_assignments a on a.user_id=u.user_id join roles r on r.role_id=a.role_id where u.user_id = any (?)";jdbc.query(connection->{var statement=connection.prepareStatement(sql);statement.setArray(1,connection.createArrayOf("varchar",ids.toArray()));return statement;},rs->{User u=new User();u.setUserId(rs.getString(1));u.setFullName(rs.getString(2));u.setEmail(rs.getString(3));u.setPhone(rs.getString(4));u.setAccountStatus(AccountStatus.valueOf(rs.getString(5)));u.setCreatedAt(PersistenceSupport.domainTime(rs.getObject(6,LocalDateTime.class),rs.getShort(7)));u.setLastLoginAt(PersistenceSupport.domainTime(rs.getObject(8,LocalDateTime.class),rs.getShort(9)));u.setRole(RoleCatalog.role(RoleName.valueOf(rs.getString(10))));values.put(u.getUserId(),u);});return values;}
    private Map<String,Vehicle> loadVehicles(List<String> ids){Map<String,Vehicle> values=new HashMap<>();if(ids.isEmpty())return values;String sql="select vehicle_id,plate_number,vehicle_type,brand,model,color,notes,user_id from vehicles where vehicle_id = any (?)";jdbc.query(connection->{var statement=connection.prepareStatement(sql);statement.setArray(1,connection.createArrayOf("varchar",ids.toArray()));return statement;},rs->{Vehicle v=new Vehicle(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7));v.setUserId(rs.getString(8));values.put(v.getVehicleId(),v);});return values;}
    private Map<String,Service> loadServices(List<String> ids){Map<String,Service> values=new HashMap<>();if(ids.isEmpty())return values;String sql="select service_id,service_name,description,legacy_price,legacy_duration_min,active,created_at,created_at_nano_remainder from service_definitions where service_id = any (?)";jdbc.query(connection->{var statement=connection.prepareStatement(sql);statement.setArray(1,connection.createArrayOf("varchar",ids.toArray()));return statement;},rs->{Service v=new Service();v.setServiceId(rs.getString(1));v.setServiceName(rs.getString(2));v.setDescription(rs.getString(3));v.setPrice(rs.getBigDecimal(4));v.setEstimatedDurationMin(rs.getInt(5));v.setActive(rs.getBoolean(6));v.setCreatedAt(PersistenceSupport.domainTime(rs.getObject(7,LocalDateTime.class),rs.getShort(8)));values.put(v.getServiceId(),v);});return values;}
}
