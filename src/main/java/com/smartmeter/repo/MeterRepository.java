package com.smartmeter.repo;

import com.smartmeter.domain.Meter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface MeterRepository extends JpaRepository<Meter, Long> {
    boolean existsByDeviceId(String deviceId);

    @Query("select m from Meter m left join fetch m.room r left join fetch r.tenant where m.deviceId = :deviceId")
    Optional<Meter> findByDeviceIdWithRoomAndTenant(@Param("deviceId") String deviceId);
}
