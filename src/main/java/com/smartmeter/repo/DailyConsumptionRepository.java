package com.smartmeter.repo;

import com.smartmeter.domain.DailyConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyConsumptionRepository extends JpaRepository<DailyConsumption, Long> {
    Optional<DailyConsumption> findByMeterIdAndDay(Long meterId, LocalDate day);

    List<DailyConsumption> findByMeterIdAndDayBetweenOrderByDay(Long meterId, LocalDate from, LocalDate to);

    @Query("select d from DailyConsumption d where d.meter.room.landlord.id = :landlordId " +
           "and d.day between :from and :to order by d.day")
    List<DailyConsumption> findForLandlordBetween(@Param("landlordId") Long landlordId,
                                                   @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select d from DailyConsumption d where d.meter.id = :meterId and d.day = :day")
    Optional<DailyConsumption> lockRow(@Param("meterId") Long meterId, @Param("day") LocalDate day);
}
