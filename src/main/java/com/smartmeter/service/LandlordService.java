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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LandlordService {
    private static final Set<String> QR_TYPES = Set.of("image/png", "image/jpeg");

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
        s.setUpiId(r.upiId().trim());
        s.setPayeeName(r.payeeName().trim());
        s.setTariffPerKwh(r.tariffPerKwh());
        boolean minChanged = s.getMinBalance().compareTo(r.minBalance()) != 0;
        s.setMinBalance(r.minBalance());
        if (minChanged) wallets.updateMinBalanceForLandlord(landlord.getId(), r.minBalance());
        return toDto(settingsRepo.save(s));
    }

    @Transactional
    public SettingsResponse uploadQr(String username, MultipartFile file) {
        if (file.isEmpty() || file.getContentType() == null || !QR_TYPES.contains(file.getContentType())) {
            throw ApiException.bad("QR must be a PNG or JPEG image");
        }
        LandlordSettings s = settingsFor(lookup.byUsername(username));
        try {
            byte[] bytes = file.getBytes();
            if (!looksLikeImage(bytes)) throw ApiException.bad("File is not a valid PNG or JPEG image");
            s.setQrImage(bytes);
        } catch (IOException e) {
            throw ApiException.bad("Could not read the uploaded file");
        }
        s.setQrContentType(file.getContentType());
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

    @Transactional
    public void vacateRoom(String username, Long roomId) {
        AppUser landlord = lookup.byUsername(username);
        Room room = rooms.findByIdAndLandlordId(roomId, landlord.getId())
                .orElseThrow(() -> ApiException.notFound("Room not found"));
        AppUser t = room.getTenant();
        if (t == null) throw ApiException.conflict("Room has no tenant");
        t.setActive(false);            // login blocked immediately; wallet and history are kept for records
        room.setTenant(null);
        room.getMeter().setManualLock(true);
        room.getMeter().setActivatedAt(null);
        meterCommands.sendRelay(room.getMeter(), false);
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

    static String qrDataUrl(LandlordSettings s) {
        if (s.getQrImage() == null) return null;
        return "data:" + s.getQrContentType() + ";base64," + Base64.getEncoder().encodeToString(s.getQrImage());
    }

    private static boolean looksLikeImage(byte[] b) {
        boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean jpg = b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
        return png || jpg;
    }

    private SettingsResponse toDto(LandlordSettings s) {
        return new SettingsResponse(s.getUpiId(), s.getPayeeName(), s.getTariffPerKwh(), s.getMinBalance(), qrDataUrl(s));
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
}

