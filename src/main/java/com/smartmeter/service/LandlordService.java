package com.smartmeter.service;

import com.smartmeter.domain.*;
import com.smartmeter.dto.Dto.*;
import com.smartmeter.repo.*;
import com.smartmeter.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LandlordService {

    private final UserLookup lookup;
    private final UserRepository users;
    private final RoomRepository rooms;
    private final MeterRepository meters;
    private final WalletRepository wallets;
    private final LandlordSettingsRepository settingsRepo;
    private final RechargeRequestRepository recharges;
    private final WalletService walletService;
    private final MeterCommandService meterCommands;
    private final PasswordEncoder encoder;

    // ---------- settings ----------
    @Transactional
    public SettingsResponse getSettings(String username) {
        return toDto(settingsFor(lookup.byUsername(username)));
    }

    @Transactional
    public SettingsResponse updateSettings(String username, SettingsRequest r) {
        AppUser landlord = lookup.byUsername(username);
        LandlordSettings s = settingsFor(landlord);
        s.setTariffPerKwh(r.tariffPerKwh());
        s.setRazorpayKeyId(r.razorpayKeyId().trim());
        s.setRazorpayKeySecret(r.razorpayKeySecret().trim());

        boolean minChanged = s.getMinBalance().compareTo(r.minBalance()) != 0;
        s.setMinBalance(r.minBalance());
        if (minChanged) wallets.updateMinBalanceForLandlord(landlord.getId(), r.minBalance());
        return toDto(settingsRepo.save(s));
    }

    // ---------- rooms / meters ----------
    @Transactional
    public RoomResponse addRoom(String username, RoomRequest r) {
        AppUser landlord = lookup.byUsername(username);
        String name = r.name().trim();
        if (rooms.existsByLandlordIdAndName(landlord.getId(), name)) throw ApiException.conflict("Room name already exists");
        if (meters.existsByDeviceId(r.deviceId())) throw ApiException.conflict("This meter is already registered");

        Meter m = new Meter();
        m.setDeviceId(r.deviceId());
        Room room = new Room();
        room.setName(name);
        room.setLandlord(landlord);
        room.setMeter(m);
        return toDto(rooms.save(room));
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> listRooms(String username) {
        AppUser landlord = lookup.byUsername(username);
        return rooms.findByLandlordIdOrderByName(landlord.getId()).stream().map(this::toDto).toList();
    }

    @Transactional
    public RoomResponse setRelay(String username, Long roomId, boolean on) {
        AppUser landlord = lookup.byUsername(username);
        Room room = rooms.findByIdAndLandlordId(roomId, landlord.getId())
                .orElseThrow(() -> ApiException.notFound("Room not found"));
        Meter m = room.getMeter();
        m.setManualLock(!on);          // landlord switching OFF blocks the automatic restore after recharge
        meterCommands.sendRelay(m, on);
        return toDto(room);
    }

    // ---------- tenants ----------
    @Transactional
    public TenantResponse onboardTenant(String username, OnboardRequest r) {
        AppUser landlord = lookup.byUsername(username);
        Room room = rooms.findByIdAndLandlordId(r.roomId(), landlord.getId())
                .orElseThrow(() -> ApiException.notFound("Room not found"));
        if (room.getTenant() != null) throw ApiException.conflict("Room already has a tenant");
        if (users.existsByUsername(r.username().toLowerCase())) throw ApiException.conflict("Email already registered");

        AppUser t = new AppUser();
        t.setUsername(r.username().toLowerCase());
        t.setPasswordHash(encoder.encode(r.password()));
        t.setFullName(r.fullName().trim());
        t.setPhone(r.phone());
        t.setRole(Role.TENANT);
        t.setLandlord(landlord);
        users.save(t);

        Wallet w = new Wallet();
        w.setTenant(t);
        w.setBalance(BigDecimal.ZERO);
        w.setMinBalance(settingsFor(landlord).getMinBalance());
        wallets.save(w);
        if (r.openingBalance().signum() > 0) {
            walletService.apply(t.getId(), TxnType.ADJUSTMENT, r.openingBalance(), "Opening balance");
        }

        room.getMeter().setActivatedAt(java.time.Instant.now());
        room.setTenant(t);
        return new TenantResponse(t.getId(), t.getFullName(), t.getUsername(), t.getPhone(), t.isActive(), room.getName());
    }


    @Transactional(readOnly = true)
    public List<TenantResponse> listTenants(String username) {
        AppUser landlord = lookup.byUsername(username);
        return users.findByLandlordIdOrderByFullName(landlord.getId()).stream()
                .map(t -> new TenantResponse(t.getId(), t.getFullName(), t.getUsername(), t.getPhone(), t.isActive(),
                        rooms.findByTenantId(t.getId()).map(Room::getName).orElse(null)))
                .toList();
    }

    // ---------- recharge history log ----------
    @Transactional(readOnly = true)
    public List<RechargeResponse> listRecharges(String username, RechargeStatus status) {
        AppUser landlord = lookup.byUsername(username);
        return recharges.findByLandlordIdAndStatusOrderByCreatedAtDesc(landlord.getId(), status)
                .stream().map(this::toDto).toList();
    }

    // ---------- helpers ----------
    LandlordSettings settingsFor(AppUser landlord) {
        return settingsRepo.findByLandlordId(landlord.getId()).orElseGet(() -> {
            LandlordSettings s = new LandlordSettings();
            s.setLandlord(landlord);
            return settingsRepo.save(s);
        });
    }

    private SettingsResponse toDto(LandlordSettings s) {
        return new SettingsResponse(s.getTariffPerKwh(), s.getMinBalance(), s.getRazorpayKeyId(), s.getRazorpayKeySecret());
    }

    private RoomResponse toDto(Room room) {
        AppUser t = room.getTenant();
        BigDecimal balance = t == null ? null : wallets.findByTenantId(t.getId()).map(Wallet::getBalance).orElse(null);
        Meter m = room.getMeter();
        return new RoomResponse(room.getId(), room.getName(), m.getDeviceId(), m.isRelayOn(), m.isManualLock(),
                m.getLastSeen(), t == null ? null : t.getId(), t == null ? null : t.getFullName(), balance,
                m.getLastPowerW(), m.getLastVoltage());
    }

    private RechargeResponse toDto(RechargeRequest r) {
        String roomName = rooms.findByTenantId(r.getTenant().getId()).map(Room::getName).orElse("-");
        return new RechargeResponse(r.getId(), r.getTenant().getFullName(), roomName, r.getAmount(),
                r.getUtr(), r.getStatus().name(), r.getCreatedAt());
    }

    @Transactional
    public void vacateRoom(String username, Long roomId) {
        AppUser landlord = lookup.byUsername(username);
        Room room = rooms.findByIdAndLandlordId(roomId, landlord.getId())
                .orElseThrow(() -> ApiException.notFound("Room not found"));
        if (room.getTenant() == null) throw ApiException.conflict("Room has no tenant");

        room.vacate();
        meterCommands.sendRelay(room.getMeter(), false);
    }
}

