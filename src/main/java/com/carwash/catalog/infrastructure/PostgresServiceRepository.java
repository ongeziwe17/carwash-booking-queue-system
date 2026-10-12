package com.carwash.catalog.infrastructure;

import com.carwash.catalog.domain.Service;
import com.carwash.catalog.domain.ServiceRepository;
import com.carwash.shared.infrastructure.PersistenceSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("postgres")
@Transactional
public class PostgresServiceRepository implements ServiceRepository {
    @Override public java.util.List<Service> findByIds(java.util.Collection<String> ids) { return repository.findAllById(ids).stream().map(this::domain).toList(); }

    @org.springframework.beans.factory.annotation.Autowired
    private com.carwash.shared.infrastructure.JpaPageQueries pages;

    @Override public com.carwash.shared.application.PageResult<Service> findPage(com.carwash.shared.application.ListQuery query) {
        var scope = query.scope();
        var clauses = new java.util.ArrayList<String>(); clauses.add("1=1");
        var parameters = new java.util.LinkedHashMap<String, Object>();
        var page = pages.find(ServiceJpaEntity.class, query, String.join(" and ", clauses), parameters, java.util.Map.ofEntries(java.util.Map.entry("id", com.carwash.shared.infrastructure.JpaPageQueries.Field.text("q.id")),
                java.util.Map.entry("name", com.carwash.shared.infrastructure.JpaPageQueries.Field.text("q.name")),
                java.util.Map.entry("price", com.carwash.shared.infrastructure.JpaPageQueries.Field.value("q.price")),
                java.util.Map.entry("duration", com.carwash.shared.infrastructure.JpaPageQueries.Field.value("q.duration")),
                java.util.Map.entry("active", com.carwash.shared.infrastructure.JpaPageQueries.Field.value("q.active"))));
        return page.map(this::domain);
    }

    private final ServiceSpringDataRepository repository;
    public PostgresServiceRepository(ServiceSpringDataRepository repository) { this.repository = repository; }

    @Override public boolean insert(Service value) { if (repository.existsById(value.getServiceId())) return false; repository.saveAndFlush(entity(value)); return true; }
    @Override public boolean update(Service value) { Optional<ServiceJpaEntity> found = repository.findById(value.getServiceId()); if (found.isEmpty()) return false; apply(value, found.get()); repository.saveAndFlush(found.get()); return true; }
    @Override public Optional<Service> findById(String id) { return repository.findById(id).map(this::domain); }
    @Override public List<Service> findAll() { return repository.findAllByOrderByIdAsc().stream().map(this::domain).toList(); }
    @Override public boolean deleteById(String id) { Optional<ServiceJpaEntity> found = repository.findById(id); if (found.isEmpty()) return false; repository.delete(found.get()); repository.flush(); return true; }
    @Override public boolean existsById(String id) { return repository.existsById(id); }

    private Service domain(ServiceJpaEntity entity) { Service value = new Service(); value.setServiceId(entity.id); value.setServiceName(entity.name); value.setDescription(entity.description); value.setPrice(entity.price); value.setEstimatedDurationMin(entity.duration); value.setActive(entity.active); value.setCreatedAt(PersistenceSupport.domainTime(entity.createdAt, entity.createdAtNano)); return value; }
    private static ServiceJpaEntity entity(Service value) { ServiceJpaEntity entity = new ServiceJpaEntity(); apply(value, entity); return entity; }
    private static void apply(Service value, ServiceJpaEntity entity) { entity.id = value.getServiceId(); entity.name = value.getServiceName(); entity.description = value.getDescription(); entity.price = value.getPrice(); entity.duration = value.getEstimatedDurationMin(); entity.active = value.isActive(); entity.createdAt = PersistenceSupport.databaseTime(value.getCreatedAt()); entity.createdAtNano = PersistenceSupport.nanoRemainder(value.getCreatedAt()); }
}
