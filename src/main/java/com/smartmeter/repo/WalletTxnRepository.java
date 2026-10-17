package com.smartmeter.repo;

import com.smartmeter.domain.WalletTxn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WalletTxnRepository extends JpaRepository<WalletTxn, Long> {
    List<WalletTxn> findTop50ByWalletIdOrderByCreatedAtDesc(Long walletId);

    List<WalletTxn> findByWalletIdAndCreatedAtBetweenOrderByCreatedAtAsc(Long walletId, Instant from, Instant to);

    @Query("select t from WalletTxn t where t.wallet.id = :walletId and t.createdAt < :before order by t.createdAt desc")
    List<WalletTxn> latestBefore(@Param("walletId") Long walletId, @Param("before") Instant before);

    default Optional<WalletTxn> findLastBefore(Long walletId, Instant before) {
        List<WalletTxn> l = latestBefore(walletId, before);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }
}
