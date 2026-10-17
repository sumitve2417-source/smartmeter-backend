package com.smartmeter.repo;

import com.smartmeter.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByLandlordIdOrderByName(Long landlordId);
    Optional<Room> findByIdAndLandlordId(Long id, Long landlordId);
    Optional<Room> findByTenantId(Long tenantId);
    boolean existsByLandlordIdAndName(Long landlordId, String name);
}
