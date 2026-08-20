package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.BusinessStatus;
import com.carwash.marketplace.domain.CarWashBusiness;
import com.carwash.marketplace.domain.CarWashBusinessRepository;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.infrastructure.PersistenceSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository @Profile("postgres") @Transactional
public class PostgresCarWashBusinessRepository implements CarWashBusinessRepository {
    private final BusinessSpringDataRepository repository;
    public PostgresCarWashBusinessRepository(BusinessSpringDataRepository repository) { this.repository=repository; }
    @Override public boolean insert(CarWashBusiness value) { if(repository.existsById(value.getBusinessId())) return false; try { repository.saveAndFlush(entity(value)); return true; } catch(DataIntegrityViolationException failure) { throw new BusinessRuleViolationException("Business conflicts with existing data"); } }
    @Override public boolean update(CarWashBusiness value) { Optional<BusinessJpaEntity> found=repository.findById(value.getBusinessId()); if(found.isEmpty()) return false; apply(value,found.get()); repository.saveAndFlush(found.get()); return true; }
    @Override public Optional<CarWashBusiness> findById(String id) { return repository.findById(id).map(this::domain); }
    @Override public List<CarWashBusiness> findAll() { return repository.findAllByOrderByIdAsc().stream().map(this::domain).toList(); }
    @Override public boolean deleteById(String id) { Optional<BusinessJpaEntity> found=repository.findById(id); if(found.isEmpty()) return false; repository.delete(found.get()); repository.flush(); return true; }
    @Override public boolean existsById(String id) { return repository.existsById(id); }
    private CarWashBusiness domain(BusinessJpaEntity e) { return new CarWashBusiness(e.id,e.name,e.email,e.phone,e.registrationNumber,BusinessStatus.valueOf(e.status),PersistenceSupport.domainTime(e.registeredAt,e.registeredAtNano),PersistenceSupport.domainTime(e.updatedAt,e.updatedAtNano)); }
    private static BusinessJpaEntity entity(CarWashBusiness v) { BusinessJpaEntity e=new BusinessJpaEntity(); apply(v,e); return e; }
    private static void apply(CarWashBusiness v,BusinessJpaEntity e) { e.id=v.getBusinessId();e.name=v.getBusinessName();e.email=v.getContactEmail();e.phone=v.getContactPhone();e.registrationNumber=v.getRegistrationNumber();e.status=v.getStatus().name();e.registeredAt=PersistenceSupport.databaseTime(v.getRegisteredAt());e.registeredAtNano=PersistenceSupport.nanoRemainder(v.getRegisteredAt());e.updatedAt=PersistenceSupport.databaseTime(v.getUpdatedAt());e.updatedAtNano=PersistenceSupport.nanoRemainder(v.getUpdatedAt()); }
}
