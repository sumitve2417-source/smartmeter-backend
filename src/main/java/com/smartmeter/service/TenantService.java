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

    @Transactional(readOnly = true)
    public TenantDashboard dashboard(String username) {
        AppUser t = lookup.byUsername(username);
        Wallet w = wallets.findByTenantId(t.getId()).orElseThrow(() -> ApiException.notFound("Wallet not found"));
        Room room = rooms.findByTenantId(t.getId()).orElse(null);
        BigDecimal tariff = settingsRepo.findByLandlordId(t.getLandlord().getId())
                .map(LandlordSettings::getTariffPerKwh).orElse(BigDecimal.ZERO);
        boolean low = w.getBalance().compareTo(w.getMinBalance().multiply(new BigDecimal("1.2"))) <= 0;
        Meter m = room == null ? null : room.getMeter();
        return new TenantDashboard(t.getFullName(), room == null ? null : room.getName(), w.getBalance(),
                w.getMinBalance(), tariff, m == null || m.isRelayOn(), low,
                m == null ? null : m.getLastPowerW(), m == null ? null : m.getLastVoltage(), m == null ? null : m.getLastSeen());
    }

    @Transactional(readOnly = true)
    public PaymentInfo paymentInfo(String username) {
        AppUser t = lookup.byUsername(username);
        LandlordSettings s = settingsRepo.findByLandlordId(t.getLandlord().getId()).orElse(null);
        if (s == null || s.getUpiId() == null) throw ApiException.conflict("Your landlord has not set up payments yet");
        String link = "upi://pay?pa=" + enc(s.getUpiId()) + "&pn=" + enc(s.getPayeeName()) + "&cu=INR";
        return new PaymentInfo(s.getUpiId(), s.getPayeeName(), LandlordService.qrDataUrl(s), link);
    }

    @Transactional
    public RechargeResponse submitRecharge(String username, RechargeSubmit r) {
        AppUser t = lookup.byUsername(username);
        String utr = r.utr().toUpperCase();
        if (recharges.existsByUtr(utr)) throw ApiException.conflict("This transaction id was already submitted");

        RechargeRequest req = new RechargeRequest();
        req.setTenant(t);
        req.setLandlord(t.getLandlord());
        req.setAmount(r.amount());
        req.setUtr(utr);
        recharges.save(req);
        return toDto(req, t);
    }

    @Transactional(readOnly = true)
    public List<RechargeResponse> myRecharges(String username) {
        AppUser t = lookup.byUsername(username);
        return recharges.findTop20ByTenantIdOrderByCreatedAtDesc(t.getId()).stream().map(r -> toDto(r, t)).toList();
    }

    @Transactional(readOnly = true)
    public List<TxnResponse> transactions(String username) {
        AppUser t = lookup.byUsername(username);
        Wallet w = wallets.findByTenantId(t.getId()).orElseThrow(() -> ApiException.notFound("Wallet not found"));
        return txns.findTop50ByWalletIdOrderByCreatedAtDesc(w.getId()).stream()
                .map(x -> new TxnResponse(x.getType().name(), x.getAmount(), x.getBalanceAfter(), x.getReference(), x.getCreatedAt()))
                .toList();
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
