package com.carwash.catalog.infrastructure;

import com.carwash.catalog.domain.ServiceOffering;
import com.carwash.catalog.domain.ServiceOfferingRepository;
import com.carwash.catalog.domain.ServiceOfferingStatus;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.infrastructure.PersistenceSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("postgres")
@Transactional
public class PostgresServiceOfferingRepository implements ServiceOfferingRepository {
    private final ServiceOfferingSpringDataRepository repository;
    public PostgresServiceOfferingRepository(ServiceOfferingSpringDataRepository repository) { this.repository = repository; }

    @Override public List<ServiceOffering> findByBranchId(String id) { return repository.findByBranchIdOrderByIdAsc(id).stream().map(this::domain).toList(); }
    @Override public List<ServiceOffering> findByServiceId(String id) { return repository.findByServiceIdOrderByIdAsc(id).stream().map(this::domain).toList(); }
    @Override public Optional<ServiceOffering> findByBranchIdAndServiceId(String branchId, String serviceId) { return repository.findByBranchIdAndServiceId(branchId, serviceId).map(this::domain); }
    @Override public Optional<ServiceOffering> findByIdAndBusinessId(String offeringId,String businessId) { return repository.findTenantScoped(offeringId,businessId).map(this::domain); }
    @Override public List<ServiceOffering> findByBranchIdAndBusinessId(String branchId,String businessId) { return repository.findByBranchTenant(branchId,businessId).stream().map(this::domain).toList(); }
    @Override public boolean existsByServiceId(String serviceId) { return repository.existsByServiceId(serviceId); }
    @Override public boolean insert(ServiceOffering value) {
        if (repository.existsById(value.getOfferingId())) return false;
        try { repository.saveAndFlush(entity(value)); return true; }
        catch (DataIntegrityViolationException failure) {
            if (PersistenceSupport.constraint(failure, "uq_offering_branch_service")) throw new BusinessRuleViolationException("Branch already has an offering for this service; reactivate or update it instead");
            throw new BusinessRuleViolationException("Service offering conflicts with existing data");
        }
    }
    @Override public boolean update(ServiceOffering value) { Optional<ServiceOfferingJpaEntity> found = repository.findById(value.getOfferingId()); if (found.isEmpty()) return false; apply(value, found.get()); repository.saveAndFlush(found.get()); return true; }
    @Override public Optional<ServiceOffering> findById(String id) { return repository.findById(id).map(this::domain); }
    @Override public List<ServiceOffering> findAll() { return repository.findAllByOrderByIdAsc().stream().map(this::domain).toList(); }
    @Override public boolean deleteById(String id) { Optional<ServiceOfferingJpaEntity> found = repository.findById(id); if (found.isEmpty()) return false; repository.delete(found.get()); repository.flush(); return true; }
    @Override public boolean existsById(String id) { return repository.existsById(id); }

    private ServiceOffering domain(ServiceOfferingJpaEntity e) { return new ServiceOffering(e.id, e.branchId, e.serviceId, e.price, e.duration, e.capacity, ServiceOfferingStatus.valueOf(e.status), PersistenceSupport.domainTime(e.createdAt, e.createdAtNano), PersistenceSupport.domainTime(e.updatedAt, e.updatedAtNano)); }
    private static ServiceOfferingJpaEntity entity(ServiceOffering value) { ServiceOfferingJpaEntity entity = new ServiceOfferingJpaEntity(); apply(value, entity); return entity; }
    private static void apply(ServiceOffering v, ServiceOfferingJpaEntity e) { e.id=v.getOfferingId(); e.branchId=v.getBranchId(); e.serviceId=v.getServiceId(); e.price=v.getPrice(); e.duration=v.getEstimatedDurationMin(); e.capacity=v.getConcurrentCapacity(); e.status=v.getStatus().name(); e.createdAt=PersistenceSupport.databaseTime(v.getCreatedAt()); e.createdAtNano=PersistenceSupport.nanoRemainder(v.getCreatedAt()); e.updatedAt=PersistenceSupport.databaseTime(v.getUpdatedAt()); e.updatedAtNano=PersistenceSupport.nanoRemainder(v.getUpdatedAt()); }
}
