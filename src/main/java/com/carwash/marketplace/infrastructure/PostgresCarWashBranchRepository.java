package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.BranchStatus;
import com.carwash.marketplace.domain.CarWashBranch;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.shared.infrastructure.PersistenceSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Repository @Profile("postgres") @Transactional
public class PostgresCarWashBranchRepository implements CarWashBranchRepository {
    private final BranchSpringDataRepository repository;
    public PostgresCarWashBranchRepository(BranchSpringDataRepository repository){this.repository=repository;}
    @Override public List<CarWashBranch> findByBusinessId(String id){return repository.findByBusinessIdOrderByIdAsc(id).stream().map(this::domain).toList();}
    @Override public Optional<CarWashBranch> findByIdAndBusinessId(String id,String businessId){return repository.findByIdAndBusinessId(id,businessId).map(this::domain);}
    @Override public boolean insert(CarWashBranch v){if(repository.existsById(v.getBranchId()))return false;repository.saveAndFlush(entity(v));return true;}
    @Override public boolean update(CarWashBranch v){Optional<BranchJpaEntity> found=repository.findById(v.getBranchId());if(found.isEmpty())return false;apply(v,found.get());repository.saveAndFlush(found.get());return true;}
    @Override public Optional<CarWashBranch> findById(String id){return repository.findById(id).map(this::domain);}
    @Override public List<CarWashBranch> findAll(){return repository.findAllByOrderByIdAsc().stream().map(this::domain).toList();}
    @Override public boolean deleteById(String id){Optional<BranchJpaEntity> found=repository.findById(id);if(found.isEmpty())return false;repository.delete(found.get());repository.flush();return true;}
    @Override public boolean existsById(String id){return repository.existsById(id);}
    private CarWashBranch domain(BranchJpaEntity e){return new CarWashBranch(e.id,e.businessId,e.name,e.address1,e.address2,e.city,e.province,e.postalCode,e.countryCode,e.latitude,e.longitude,e.timezone,BranchStatus.valueOf(e.status),e.publicDiscovery,PersistenceSupport.domainTime(e.createdAt,e.createdAtNano),PersistenceSupport.domainTime(e.updatedAt,e.updatedAtNano));}
    private static BranchJpaEntity entity(CarWashBranch v){BranchJpaEntity e=new BranchJpaEntity();apply(v,e);return e;}
    private static void apply(CarWashBranch v,BranchJpaEntity e){e.id=v.getBranchId();e.businessId=v.getBusinessId();e.name=v.getBranchName();e.address1=v.getAddressLine1();e.address2=v.getAddressLine2();e.city=v.getCity();e.province=v.getProvince();e.postalCode=v.getPostalCode();e.countryCode=v.getCountryCode();e.latitude=v.getLatitude();e.longitude=v.getLongitude();e.timezone=v.getTimezone();e.status=v.getStatus().name();e.publicDiscovery=v.isPublicDiscoveryEnabled();e.createdAt=PersistenceSupport.databaseTime(v.getCreatedAt());e.createdAtNano=PersistenceSupport.nanoRemainder(v.getCreatedAt());e.updatedAt=PersistenceSupport.databaseTime(v.getUpdatedAt());e.updatedAtNano=PersistenceSupport.nanoRemainder(v.getUpdatedAt());}
}
