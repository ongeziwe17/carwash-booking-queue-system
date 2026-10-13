package com.carwash.booking.infrastructure;

import com.carwash.booking.application.BookingReportBucket;
import com.carwash.booking.application.BookingReportQuery;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.booking.domain.BookingStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.*;

@Component @Profile("!postgres")
public class InMemoryBookingReportQuery implements BookingReportQuery {
    private final BookingRepository bookings;
    public InMemoryBookingReportQuery(BookingRepository bookings) { this.bookings = bookings; }
    @Override public List<BookingReportBucket> scheduledCohorts(String branchId, LocalDate start, LocalDate end) {
        record Key(LocalDate day, int hour, String offering, BookingStatus status) { }
        Map<Key,Long> counts = new HashMap<>();
        for (var booking : bookings.findByBranchId(branchId)) {
            var time = booking.getScheduledDateTime();
            if (time != null && !time.toLocalDate().isBefore(start) && time.toLocalDate().isBefore(end)) {
                counts.merge(new Key(time.toLocalDate(), time.getHour(), booking.getServiceOfferingId(),
                        booking.getStatus()), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream().map(e -> new BookingReportBucket(e.getKey().day(), e.getKey().hour(),
                e.getKey().offering(), e.getKey().status(), e.getValue())).toList();
    }
    @Override public BigDecimal bookedMinutes(String branchId, LocalDate day, Map<String,Integer> durations) {
        LocalDateTime from = day.atStartOfDay(), to = day.plusDays(1).atStartOfDay();
        long seconds = 0;
        for (var booking : bookings.findByBranchId(branchId)) {
            if (booking.getStatus() == BookingStatus.CANCELLED || booking.getScheduledDateTime() == null) continue;
            Integer minutes = durations.get(booking.getServiceOfferingId());
            if (minutes == null) continue;
            var at = booking.getScheduledDateTime();
            var until = at.plusMinutes(minutes);
            if (at.isBefore(to) && until.isAfter(from))
                seconds += Duration.between(at.isAfter(from) ? at : from, until.isBefore(to) ? until : to).getSeconds();
        }
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
    }
}
