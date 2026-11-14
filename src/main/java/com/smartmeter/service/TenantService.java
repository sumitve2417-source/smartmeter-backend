package com.smartmeter.service;

import com.smartmeter.domain.*;
import com.smartmeter.dto.Dto.*;
import com.smartmeter.repo.*;
import com.smartmeter.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {
    private final UserLookup lookup;
    private final RoomRepository rooms;
    private final WalletRepository wallets;
    private final WalletTxnRepository txns;
    private final LandlordSettingsRepository settingsRepo;
    private final RechargeRequestRepository recharges;
    private final WalletService walletService;
    private final MeterCommandService meterCommands;

    @Transactional(readOnly = true)
    public TenantDashboard dashboard(String username) {
        AppUser tenant = lookup.byUsername(username);
        Wallet wallet = findWallet(tenant.getId());
        Room room = rooms.findByTenantId(tenant.getId()).orElse(null);
        BigDecimal tariff = fetchTariff(tenant.getLandlord().getId());
        boolean isLowBalance = wallet.getBalance().compareTo(wallet.getMinBalance().multiply(new BigDecimal("1.2"))) <= 0;
        Meter meter = room == null ? null : room.getMeter();

        return new TenantDashboard(
                tenant.getFullName(),
                room == null ? null : room.getName(),
                wallet.getBalance(),
                wallet.getMinBalance(),
                tariff,
                meter == null || meter.isRelayOn(),
                isLowBalance,
                meter == null ? null : meter.getLastPowerW(),
                meter == null ? null : meter.getLastVoltage(),
                meter == null ? null : meter.getLastSeen()
        );
    }

    @Transactional(readOnly = true)
    public PaymentInfo paymentInfo(String username) {
        AppUser tenant = lookup.byUsername(username);
        LandlordSettings settings = settingsRepo.findByLandlordId(tenant.getLandlord().getId()).orElse(null);
        if (settings == null || settings.getUpiId() == null) {
            throw ApiException.conflict("Your landlord has not set up payments yet");
        }
        String upiLink = "upi://pay?pa=" + enc(settings.getUpiId()) + "&pn=" + enc(settings.getPayeeName()) + "&cu=INR";
        return new PaymentInfo(settings.getUpiId(), settings.getPayeeName(), LandlordService.qrDataUrl(settings), upiLink);
    }

    @Transactional
    public RechargeResponse submitRecharge(String username, RechargeSubmit r) {
        AppUser tenant = lookup.byUsername(username);
        String utr = validateAndNormalizeUtr(r.utr());

        RechargeRequest rechargeRequest = saveApprovedRecharge(tenant, r.amount(), utr);
        Wallet updatedWallet = creditTenantWallet(tenant.getId(), r.amount(), utr);
        restorePowerIfEligible(tenant.getId(), updatedWallet);

        return toDto(rechargeRequest, tenant);
    }

    @Transactional(readOnly = true)
    public List<RechargeResponse> myRecharges(String username) {
        AppUser tenant = lookup.byUsername(username);
        return recharges.findTop20ByTenantIdOrderByCreatedAtDesc(tenant.getId())
                .stream()
                .map(r -> toDto(r, tenant))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TxnResponse> transactions(String username) {
        AppUser tenant = lookup.byUsername(username);
        Wallet wallet = findWallet(tenant.getId());
        return txns.findTop50ByWalletIdOrderByCreatedAtDesc(wallet.getId())
                .stream()
                .map(x -> new TxnResponse(x.getType().name(), x.getAmount(), x.getBalanceAfter(), x.getReference(), x.getCreatedAt()))
                .toList();
    }

    // ---------- Single-Responsibility Clean Code Helpers ----------

    private Wallet findWallet(Long tenantId) {
        return wallets.findByTenantId(tenantId)
                .orElseThrow(() -> ApiException.notFound("Wallet not found"));
    }

    private BigDecimal fetchTariff(Long landlordId) {
        return settingsRepo.findByLandlordId(landlordId)
                .map(LandlordSettings::getTariffPerKwh)
                .orElse(BigDecimal.ZERO);
    }

    private String validateAndNormalizeUtr(String rawUtr) {
        String utr = rawUtr.toUpperCase();
        if (recharges.existsByUtr(utr)) {
            throw ApiException.conflict("This transaction id was already submitted");
        }
        return utr;
    }

    private RechargeRequest saveApprovedRecharge(AppUser tenant, BigDecimal amount, String utr) {
        RechargeRequest req = new RechargeRequest();
        req.setTenant(tenant);
        req.setLandlord(tenant.getLandlord());
        req.setAmount(amount);
        req.setUtr(utr);
        req.setStatus(RechargeStatus.APPROVED);
        req.setDecidedAt(Instant.now());
        return recharges.save(req);
    }

    private Wallet creditTenantWallet(Long tenantId, BigDecimal amount, String utr) {
        return walletService.apply(tenantId, TxnType.RECHARGE, amount, "Instant UPI " + utr);
    }

    private void restorePowerIfEligible(Long tenantId, Wallet updatedWallet) {
        rooms.findByTenantId(tenantId).ifPresent(room -> {
            Meter meter = room.getMeter();
            boolean isPowerCut = !meter.isRelayOn();
            boolean notManuallyLocked = !meter.isManualLock();
            boolean balanceAboveMinimum = updatedWallet.getBalance().compareTo(updatedWallet.getMinBalance()) > 0;

            if (isPowerCut && notManuallyLocked && balanceAboveMinimum) {
                meterCommands.sendRelay(meter, true);
            }
        });
    }

    private RechargeResponse toDto(RechargeRequest r, AppUser t) {
        String room = rooms.findByTenantId(t.getId()).map(Room::getName).orElse("-");
        return new RechargeResponse(r.getId(), t.getFullName(), room, r.getAmount(), r.getUtr(),
                r.getStatus().name(), r.getCreatedAt());
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
