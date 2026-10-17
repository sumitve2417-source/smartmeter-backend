package com.smartmeter.repo;

import com.smartmeter.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByTenantId(Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.tenant.id = :tenantId")
    Optional<Wallet> lockByTenantId(@Param("tenantId") Long tenantId);

    @Modifying(clearAutomatically = true)
    @Query("update Wallet w set w.minBalance = :min where w.tenant.id in " +
           "(select u.id from AppUser u where u.landlord.id = :landlordId)")
    int updateMinBalanceForLandlord(@Param("landlordId") Long landlordId, @Param("min") BigDecimal min);
}
