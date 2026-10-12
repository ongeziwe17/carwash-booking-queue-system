package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.ClosureStatus;
import com.carwash.marketplace.domain.TemporaryBranchClosure;
import com.carwash.marketplace.domain.TemporaryBranchClosureRepository;
import com.carwash.shared.infrastructure.PersistenceSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository @Profile("postgres") @Transactional
public class PostgresTemporaryBranchClosureRepository implements TemporaryBranchClosureRepository {
    @org.springframework.beans.factory.annotation.Autowired
    private com.carwash.shared.infrastructure.JpaPageQueries pages;

    @Override public com.carwash.shared.application.PageResult<TemporaryBranchClosure> findPage(com.carwash.shared.application.ListQuery query) {
        var scope = query.scope();
        var clauses = new java.util.ArrayList<String>(); clauses.add("1=1");
        var parameters = new java.util.LinkedHashMap<String, Object>();
        if (scope.businessId() != null) { clauses.add("exists (select b.id from BranchJpaEntity b where b.id=q.branchId and b.businessId=:business)"); parameters.put("business", scope.businessId()); }
        if (scope.branchId() != null) { clauses.add("q.branchId = :branch"); parameters.put("branch", scope.branchId()); }
        var page = pages.find(ClosureJpaEntity.class, query, String.join(" and ", clauses), parameters, java.util.Map.ofEntries(java.util.Map.entry("id", com.carwash.shared.infrastructure.JpaPageQueries.Field.text("q.id")),
                java.util.Map.entry("startsAt", com.carwash.shared.infrastructure.JpaPageQueries.Field.time("q.startSecond", "q.startNano")),
                java.util.Map.entry("endsAt", com.carwash.shared.infrastructure.JpaPageQueries.Field.time("q.endSecond", "q.endNano")),
                java.util.Map.entry("status", com.carwash.shared.infrastructure.JpaPageQueries.Field.text("q.status")),
                java.util.Map.entry("from", com.carwash.shared.infrastructure.JpaPageQueries.Field.time("q.startSecond", "q.startNano")),
                java.util.Map.entry("to", com.carwash.shared.infrastructure.JpaPageQueries.Field.time("q.startSecond", "q.startNano"))));
        return page.map(this::domain);
    }

    private final ClosureSpringDataRepository repository;
    public PostgresTemporaryBranchClosureRepository(ClosureSpringDataRepository repository){this.repository=repository;}
    @Override public List<TemporaryBranchClosure> findByBranchId(String id){return repository.findByBranchIdOrderByIdAsc(id).stream().map(this::domain).toList();}
    @Override public List<TemporaryBranchClosure> findByBranchIdAndBusinessId(String branchId,String businessId){return repository.findByBranchTenant(branchId,businessId).stream().map(this::domain).toList();}
    @Override public Optional<TemporaryBranchClosure> findByIdAndBusinessId(String closureId,String businessId){return repository.findTenantScoped(closureId,businessId).map(this::domain);}
    @Override public boolean updateForBusiness(TemporaryBranchClosure v,String businessId){
        Optional<ClosureJpaEntity> found=repository.findTenantScoped(v.getClosureId(),businessId);
        return found.isPresent()&&guardedUpdate(v,found.get().version,businessId);
    }
    @Override public boolean updateForAdministrator(TemporaryBranchClosure v){
        Optional<ClosureJpaEntity> found=repository.findById(v.getClosureId());
        return found.isPresent()&&guardedUpdate(v,found.get().version,null);
    }
    @Override public boolean insert(TemporaryBranchClosure v){if(repository.existsById(v.getClosureId()))return false;repository.saveAndFlush(entity(v));return true;}
    @Override public boolean update(TemporaryBranchClosure v){Optional<ClosureJpaEntity> found=repository.findById(v.getClosureId());if(found.isEmpty())return false;apply(v,found.get());repository.saveAndFlush(found.get());return true;}
    @Override public Optional<TemporaryBranchClosure> findById(String id){return repository.findById(id).map(this::domain);}
    @Override public List<TemporaryBranchClosure> findAll(){return repository.findAllByOrderByIdAsc().stream().map(this::domain).toList();}
    @Override public boolean deleteById(String id){Optional<ClosureJpaEntity> found=repository.findById(id);if(found.isEmpty())return false;repository.delete(found.get());repository.flush();return true;}
    @Override public boolean existsById(String id){return repository.existsById(id);}
    private boolean guardedUpdate(TemporaryBranchClosure v,Long version,String businessId){
        int rows=businessId==null
                ? repository.updateAdministratorScoped(
                        v.getClosureId(),v.getStatus().name(),PersistenceSupport.databaseTime(v.getUpdatedAt()),
                        PersistenceSupport.nanoRemainder(v.getUpdatedAt()),version)
                : repository.updateBusinessScoped(
                        v.getClosureId(),businessId,v.getStatus().name(),
                        PersistenceSupport.databaseTime(v.getUpdatedAt()),
                        PersistenceSupport.nanoRemainder(v.getUpdatedAt()),version);
        return rows==1;
    }
    private TemporaryBranchClosure domain(ClosureJpaEntity e){return new TemporaryBranchClosure(e.id,e.branchId,Instant.ofEpochSecond(e.startSecond,e.startNano),Instant.ofEpochSecond(e.endSecond,e.endNano),e.reason,ClosureStatus.valueOf(e.status),PersistenceSupport.domainTime(e.createdAt,e.createdAtNano),PersistenceSupport.domainTime(e.updatedAt,e.updatedAtNano));}
    private static ClosureJpaEntity entity(TemporaryBranchClosure v){ClosureJpaEntity e=new ClosureJpaEntity();apply(v,e);return e;}
    private static void apply(TemporaryBranchClosure v,ClosureJpaEntity e){e.id=v.getClosureId();e.branchId=v.getBranchId();e.startSecond=v.getStartAt().getEpochSecond();e.startNano=v.getStartAt().getNano();e.endSecond=v.getEndAt().getEpochSecond();e.endNano=v.getEndAt().getNano();e.reason=v.getReason();e.status=v.getStatus().name();e.createdAt=PersistenceSupport.databaseTime(v.getCreatedAt());e.createdAtNano=PersistenceSupport.nanoRemainder(v.getCreatedAt());e.updatedAt=PersistenceSupport.databaseTime(v.getUpdatedAt());e.updatedAtNano=PersistenceSupport.nanoRemainder(v.getUpdatedAt());}
}
