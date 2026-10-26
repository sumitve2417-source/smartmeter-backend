package com.smartmeter.repo;

import com.smartmeter.domain.Reading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReadingRepository extends JpaRepository<Reading, Long> {
    @Query("select r from Reading r where r.meter.id = :meterId and r.recordedAt >= :from order by r.recordedAt")
    List<Reading> findSince(@Param("meterId") Long meterId, @Param("from") LocalDateTime from);

    @Query("select r from Reading r where r.meter.id = :meterId and r.recordedAt >= :from and r.recordedAt < :to order by r.recordedAt")
    List<Reading> findBetween(@Param("meterId") Long meterId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(r.deltaKwh),0) from Reading r where r.meter.id = :meterId " +
            "and r.recordedAt >= :from and r.recordedAt < :to")
    double sumKwhBetween(@Param("meterId") Long meterId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(r.cost),0) from Reading r where r.meter.room.landlord.id = :landlordId " +
            "and r.recordedAt >= :from and r.recordedAt < :to")
    java.math.BigDecimal sumRevenueForLandlord(@Param("landlordId") Long landlordId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
