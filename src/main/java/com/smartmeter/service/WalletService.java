package com.smartmeter.service;

import com.smartmeter.domain.*;
import com.smartmeter.repo.WalletRepository;
import com.smartmeter.repo.WalletTxnRepository;
import com.smartmeter.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {
    private final WalletRepository wallets;
    private final WalletTxnRepository txns;

    /** Adds delta (negative to deduct) under a row lock and writes the ledger entry. Must run inside a transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Wallet apply(Long tenantId, TxnType type, BigDecimal delta, String reference) {
        Wallet w = wallets.lockByTenantId(tenantId)
                .orElseThrow(() -> ApiException.notFound("Wallet not found"));
        BigDecimal newBalance = w.getBalance().add(delta);
        w.setBalance(newBalance);

        WalletTxn t = new WalletTxn();
        t.setWallet(w);
        t.setType(type);
        t.setAmount(delta);
        t.setBalanceAfter(newBalance);
        t.setReference(reference);
        txns.save(t);
        return w;
    }
}
