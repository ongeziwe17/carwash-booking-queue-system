package com.carwash.booking.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

interface BookingSpringDataRepository extends JpaRepository<BookingJpaEntity,String> {
    List<BookingJpaEntity> findAllByOrderByIdAsc();
    List<BookingJpaEntity> findByUserIdOrderByIdAsc(String id);
    List<BookingJpaEntity> findByVehicleIdOrderByIdAsc(String id);
    List<BookingJpaEntity> findByServiceIdOrderByIdAsc(String id);
    List<BookingJpaEntity> findByBranchIdOrderByIdAsc(String id);
    List<BookingJpaEntity> findByOfferingIdOrderByIdAsc(String id);
    List<BookingJpaEntity> findByScheduledAtAndScheduledAtNanoOrderByIdAsc(LocalDateTime time, short nano);
    boolean existsByUserId(String id);
    boolean existsByVehicleId(String id);
    boolean existsByServiceId(String id);
    boolean existsByOfferingId(String id);
}
