package com.smartmeter.service;

import com.smartmeter.domain.*;
import com.smartmeter.mqtt.TelemetryPayload;
import com.smartmeter.repo.DailyConsumptionRepository;
import com.smartmeter.repo.LandlordSettingsRepository;
import com.smartmeter.repo.MeterRepository;
import com.smartmeter.repo.ReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * One telemetry sample in, one billing decision out. This is the core loop:
 * delta kWh since last sample -> deduct wallet -> cut relay if below minimum -> restore/alert as needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {
    private static final BigDecimal DEFAULT_TARIFF = new BigDecimal("8.00");
    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
    // Year 2024 threshold in epoch millis (approx 1704067200000L) to catch un-synced ESP32 boot timestamps
    private static final long MIN_VALID_EPOCH = 1704067200000L;

    private final MeterRepository meters;
    private final ReadingRepository readings;
    private final DailyConsumptionRepository dailyConsumption;
    private final LandlordSettingsRepository landlordSettings;
    private final WalletService walletService;
    private final MeterCommandService meterCommands;
    private final SmsService smsService;

    @Transactional
    public void handle(TelemetryPayload p) {
        Meter meter = meters.findByDeviceIdWithRoomAndTenant(p.deviceId()).orElse(null);
        if (meter == null) {
            log.warn("Telemetry from unknown device '{}' - ignored", p.deviceId());
            return;
        }
        meter.setLastSeen(Instant.now());
        meter.setLastVoltage(p.voltage());
        meter.setLastCurrent(p.current());
        meter.setLastPowerW(p.powerW());
        meter.setLastFrequency(p.frequency());
        meter.setLastPowerFactor(p.powerFactor());

        // Guard against a bad/reset counter: never bill a negative delta.
        double delta = p.energyKwh() - meter.getLastEnergyKwh();
        if (delta < 0) delta = 0;
        meter.setLastEnergyKwh(p.energyKwh());

        Room room = meter.getRoom();
        // A meter only bills once a tenant is onboarded into its room and activated.
        if (room == null || room.getTenant() == null || meter.getActivatedAt() == null) {
            saveReading(meter, p, delta, BigDecimal.ZERO);
            return;
        }

        AppUser tenant = room.getTenant();
        BigDecimal tariff = landlordSettings.findByLandlordId(room.getLandlord().getId())
                .map(LandlordSettings::getTariffPerKwh).orElse(DEFAULT_TARIFF);
        BigDecimal cost = tariff.multiply(BigDecimal.valueOf(delta)).setScale(2, RoundingMode.HALF_UP);
        saveReading(meter, p, delta, cost);
        if (cost.signum() <= 0) return;

        Instant resolvedInstant = resolveValidInstant(p.ts());
        upsertDailyConsumption(meter, resolvedInstant, delta, cost);

        Wallet wallet = walletService.apply(tenant.getId(), TxnType.USAGE, cost.negate(), "Usage " + trimKwh(delta) + " kWh");

        boolean shouldBeOn = wallet.getBalance().compareTo(wallet.getMinBalance()) > 0;
        if (!shouldBeOn && meter.isRelayOn()) {
            meterCommands.sendRelay(meter, false);
            smsService.sendLowBalanceCut(tenant, wallet.getBalance());
        } else if (shouldBeOn && !meter.isRelayOn() && !meter.isManualLock()) {
            meterCommands.sendRelay(meter, true);
        } else if (shouldBeOn
                && wallet.getBalance().compareTo(wallet.getMinBalance().multiply(new BigDecimal("1.2"))) <= 0) {
            smsService.sendLowBalanceWarning(tenant, wallet.getBalance());
        }
    }

    /** Validates device epoch timestamp; falls back to current time if ESP32 hasn't synced NTP yet. */
    private Instant resolveValidInstant(long rawTs) {
        if (rawTs < MIN_VALID_EPOCH) {
            return Instant.now();
        }
        return Instant.ofEpochMilli(rawTs);
    }

    /** One row per meter per local calendar day, keeping the Month view correctly grouped by dates. */
    private void upsertDailyConsumption(Meter meter, Instant ts, double delta, BigDecimal cost) {
        LocalDate day = ts.atZone(INDIA_ZONE).toLocalDate();
        DailyConsumption row = dailyConsumption.findByMeterIdAndDay(meter.getId(), day).orElseGet(() -> {
            DailyConsumption d = new DailyConsumption();
            d.setMeter(meter);
            d.setDay(day);
            return d;
        });
        row.setKwh(row.getKwh() + delta);
        row.setRevenue(row.getRevenue().add(cost));
        dailyConsumption.save(row);
    }

    private void saveReading(Meter meter, TelemetryPayload p, double delta, BigDecimal cost) {
        Reading r = new Reading();
        r.setMeter(meter);
        r.setVoltage(p.voltage());
        r.setCurrent(p.current());
        r.setPowerW(p.powerW());
        r.setEnergyKwh(p.energyKwh());
        r.setFrequency(p.frequency());
        r.setPowerFactor(p.powerFactor());
        r.setDeltaKwh(delta);
        r.setCost(cost);

        Instant resolvedInstant = resolveValidInstant(p.ts());
        LocalDateTime localRecordedAt = LocalDateTime.ofInstant(resolvedInstant, INDIA_ZONE);
        r.setRecordedAt(localRecordedAt);

        readings.save(r);
    }

    private static String trimKwh(double d) {
        return BigDecimal.valueOf(d).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
