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
        if (settings == null || settings.getRazorpayKeyId() == null) {
            throw ApiException.conflict("Your landlord has not configured Razorpay gateway keys yet");
        }
        return new PaymentInfo(settings.getRazorpayKeyId());
    }

    @Transactional
    public PaymentOrderResponse createPaymentOrder(String username, BigDecimal amount) {
        AppUser tenant = lookup.byUsername(username);
        Room room = rooms.findByTenantId(tenant.getId())
                .orElseThrow(() -> ApiException.notFound("No room assigned"));

        LandlordSettings settings = settingsRepo.findByLandlordId(tenant.getLandlord().getId())
                .orElseThrow(() -> ApiException.conflict("Landlord settings not found"));

        if (settings.getRazorpayKeyId() == null || settings.getRazorpayKeySecret() == null) {
            throw ApiException.conflict("Landlord has not configured Razorpay keys yet");
        }

        try {
            com.razorpay.RazorpayClient razorpay = new com.razorpay.RazorpayClient(settings.getRazorpayKeyId(), settings.getRazorpayKeySecret());

            org.json.JSONObject orderRequest = new org.json.JSONObject();
            orderRequest.put("amount", amount.multiply(new BigDecimal(100)).intValue()); // Amount in paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "room_" + room.getId() + "_" + System.currentTimeMillis());

            com.razorpay.Order order = razorpay.orders.create(orderRequest);
            String orderId = order.get("id");

            // Save a pending recharge record locked to this official gateway order ID
            RechargeRequest req = new RechargeRequest();
            req.setTenant(tenant);
            req.setLandlord(tenant.getLandlord());
            req.setAmount(amount);
            req.setUtr(orderId);
            req.setStatus(RechargeStatus.PENDING);
            recharges.save(req);

            return new PaymentOrderResponse(orderId, amount, settings.getRazorpayKeyId(), room.getName());
        } catch (Exception e) {
            throw ApiException.bad("Could not initialize payment gateway order: " + e.getMessage());
        }
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

    private RechargeResponse toDto(RechargeRequest r, AppUser t) {
        String room = rooms.findByTenantId(t.getId()).map(Room::getName).orElse("-");
        return new RechargeResponse(r.getId(), t.getFullName(), room, r.getAmount(), r.getUtr(),
                r.getStatus().name(), r.getCreatedAt());
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
