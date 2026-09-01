package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.BranchOperatingSchedule;
import com.carwash.marketplace.domain.BranchOperatingScheduleRepository;
import com.carwash.marketplace.domain.WeeklyOperatingInterval;
import com.carwash.shared.infrastructure.PersistenceSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository @Profile("postgres") @Transactional
public class PostgresBranchOperatingScheduleRepository implements BranchOperatingScheduleRepository {
    private final ScheduleSpringDataRepository schedules; private final WeeklyIntervalSpringDataRepository intervals;
    public PostgresBranchOperatingScheduleRepository(ScheduleSpringDataRepository schedules,WeeklyIntervalSpringDataRepository intervals){this.schedules=schedules;this.intervals=intervals;}
    @Override public boolean insert(BranchOperatingSchedule v){if(schedules.existsById(v.getBranchId()))return false;schedules.saveAndFlush(entity(v));saveIntervals(v);return true;}
    @Override public boolean update(BranchOperatingSchedule v){Optional<ScheduleJpaEntity> found=schedules.findById(v.getBranchId());if(found.isEmpty())return false;apply(v,found.get());schedules.saveAndFlush(found.get());intervals.deleteByBranchId(v.getBranchId());intervals.flush();saveIntervals(v);return true;}
    @Override public Optional<BranchOperatingSchedule> findById(String id){return schedules.findById(id).map(this::domain);}
    @Override public Optional<BranchOperatingSchedule> findByBranchIdAndBusinessId(String branchId,String businessId){return schedules.findTenantScoped(branchId,businessId).map(this::domain);}
    @Override public boolean updateForBusiness(BranchOperatingSchedule v,String businessId){
        Optional<ScheduleJpaEntity> found=schedules.findTenantScoped(v.getBranchId(),businessId);
        return found.isPresent()&&guardedUpdate(v,found.get().version,businessId);
    }
    @Override public boolean updateForAdministrator(BranchOperatingSchedule v){
        Optional<ScheduleJpaEntity> found=schedules.findById(v.getBranchId());
        return found.isPresent()&&guardedUpdate(v,found.get().version,null);
    }
    @Override public List<BranchOperatingSchedule> findAll(){List<ScheduleJpaEntity> found=schedules.findAllByOrderByBranchIdAsc();if(found.isEmpty())return List.of();Map<String,List<WeeklyIntervalJpaEntity>> byBranch=intervals.findByBranchIdInOrderByBranchIdAscOrderAsc(found.stream().map(e->e.branchId).toList()).stream().collect(Collectors.groupingBy(e->e.branchId));return found.stream().map(e->domain(e,byBranch.getOrDefault(e.branchId,List.of()))).toList();}
    @Override public boolean deleteById(String id){Optional<ScheduleJpaEntity> found=schedules.findById(id);if(found.isEmpty())return false;schedules.delete(found.get());schedules.flush();return true;}
    @Override public boolean existsById(String id){return schedules.existsById(id);}
    private BranchOperatingSchedule domain(ScheduleJpaEntity e){return domain(e,intervals.findByBranchIdOrderByOrderAsc(e.branchId));}
    private BranchOperatingSchedule domain(ScheduleJpaEntity e,List<WeeklyIntervalJpaEntity> source){List<WeeklyOperatingInterval> values=source.stream().map(i->new WeeklyOperatingInterval(DayOfWeek.of(i.day),LocalTime.ofNanoOfDay(i.opensNano),LocalTime.ofNanoOfDay(i.closesNano))).toList();return new BranchOperatingSchedule(e.branchId,values,PersistenceSupport.domainTime(e.createdAt,e.createdAtNano),PersistenceSupport.domainTime(e.updatedAt,e.updatedAtNano));}
    private static ScheduleJpaEntity entity(BranchOperatingSchedule v){ScheduleJpaEntity e=new ScheduleJpaEntity();apply(v,e);return e;}
    private boolean guardedUpdate(BranchOperatingSchedule v,Long version,String businessId){
        int rows=businessId==null
                ? schedules.updateAdministratorScoped(
                        v.getBranchId(),PersistenceSupport.databaseTime(v.getUpdatedAt()),
                        PersistenceSupport.nanoRemainder(v.getUpdatedAt()),version)
                : schedules.updateBusinessScoped(
                        v.getBranchId(),businessId,PersistenceSupport.databaseTime(v.getUpdatedAt()),
                        PersistenceSupport.nanoRemainder(v.getUpdatedAt()),version);
        if(rows!=1)return false;
        intervals.deleteByBranchId(v.getBranchId());
        intervals.flush();
        saveIntervals(v);
        return true;
    }
    private static void apply(BranchOperatingSchedule v,ScheduleJpaEntity e){e.branchId=v.getBranchId();e.createdAt=PersistenceSupport.databaseTime(v.getCreatedAt());e.createdAtNano=PersistenceSupport.nanoRemainder(v.getCreatedAt());e.updatedAt=PersistenceSupport.databaseTime(v.getUpdatedAt());e.updatedAtNano=PersistenceSupport.nanoRemainder(v.getUpdatedAt());}
    private void saveIntervals(BranchOperatingSchedule v){int[] index={0};intervals.saveAll(v.getIntervals().stream().map(i->{WeeklyIntervalJpaEntity e=new WeeklyIntervalJpaEntity();e.branchId=v.getBranchId();e.order=index[0]++;e.day=(short)i.dayOfWeek().getValue();e.opensNano=i.opensAt().toNanoOfDay();e.closesNano=i.closesAt().toNanoOfDay();return e;}).toList());intervals.flush();}
}
