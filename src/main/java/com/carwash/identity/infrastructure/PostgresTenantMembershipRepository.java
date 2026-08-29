package com.carwash.identity.infrastructure;

import com.carwash.identity.domain.TenantMembership;
import com.carwash.identity.domain.TenantMembershipRepository;
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
public class PostgresTenantMembershipRepository implements TenantMembershipRepository {

    private final TenantMembershipSpringDataRepository repository;

    public PostgresTenantMembershipRepository(TenantMembershipSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<TenantMembership> findByBusinessId(String businessId) {
        return repository.findByBusinessIdOrderByUserIdAsc(businessId).stream().map(this::domain).toList();
    }

    @Override
    public boolean insert(TenantMembership membership) {
        if (repository.existsById(membership.userId())) return false;
        try {
            repository.saveAndFlush(entity(membership));
            return true;
        } catch (DataIntegrityViolationException failure) {
            throw new BusinessRuleViolationException("Tenant membership conflicts with current persistent data");
        }
    }

    @Override
    public boolean update(TenantMembership membership) {
        Optional<TenantMembershipJpaEntity> found = repository.findById(membership.userId());
        if (found.isEmpty()) return false;
        apply(membership, found.get());
        repository.saveAndFlush(found.get());
        return true;
    }

    @Override
    public Optional<TenantMembership> findById(String userId) {
        return repository.findById(userId).map(this::domain);
    }

    @Override
    public List<TenantMembership> findAll() {
        return repository.findAllByOrderByUserIdAsc().stream().map(this::domain).toList();
    }

    @Override
    public boolean deleteById(String userId) {
        Optional<TenantMembershipJpaEntity> found = repository.findById(userId);
        if (found.isEmpty()) return false;
        repository.delete(found.get());
        repository.flush();
        return true;
    }

    @Override
    public boolean existsById(String userId) {
        return repository.existsById(userId);
    }

    private TenantMembership domain(TenantMembershipJpaEntity entity) {
        return new TenantMembership(
                entity.userId,
                entity.businessId,
                PersistenceSupport.domainTime(entity.assignedAt, entity.assignedAtNano));
    }

    private static TenantMembershipJpaEntity entity(TenantMembership membership) {
        TenantMembershipJpaEntity entity = new TenantMembershipJpaEntity();
        apply(membership, entity);
        return entity;
    }

    private static void apply(TenantMembership membership, TenantMembershipJpaEntity entity) {
        entity.userId = membership.userId();
        entity.businessId = membership.businessId();
        entity.assignedAt = PersistenceSupport.databaseTime(membership.assignedAt());
        entity.assignedAtNano = PersistenceSupport.nanoRemainder(membership.assignedAt());
    }
}
