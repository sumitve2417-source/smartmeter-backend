package com.smartmeter.repo;

import com.smartmeter.domain.RechargeRequest;
import com.smartmeter.domain.RechargeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RechargeRequestRepository extends JpaRepository<RechargeRequest, Long> {
    List<RechargeRequest> findByLandlordIdAndStatusOrderByCreatedAtDesc(Long landlordId, RechargeStatus status);
    List<RechargeRequest> findTop20ByTenantIdOrderByCreatedAtDesc(Long tenantId);
    Optional<RechargeRequest> findByIdAndLandlordId(Long id, Long landlordId);
    boolean existsByUtr(String utr);
}
