package com.smartmeter.repo;

import com.smartmeter.domain.AppUser;
import com.smartmeter.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByRole(Role role);
    boolean existsByUsernameAndIdNot(String username, Long id);
    List<AppUser> findByLandlordIdOrderByFullName(Long landlordId);
}
