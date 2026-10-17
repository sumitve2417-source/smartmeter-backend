package com.smartmeter.repo;

import com.smartmeter.domain.LandlordSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LandlordSettingsRepository extends JpaRepository<LandlordSettings, Long> {
    Optional<LandlordSettings> findByLandlordId(Long landlordId);
}
