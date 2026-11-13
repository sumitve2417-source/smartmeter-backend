package com.smartmeter.service;

import com.smartmeter.domain.*;
import com.smartmeter.mqtt.TelemetryPayload;
import com.smartmeter.repo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock private MeterRepository meters;
    @Mock private ReadingRepository readings;
    @Mock private DailyConsumptionRepository dailyConsumption;
    @Mock private LandlordSettingsRepository landlordSettings;
    @Mock private WalletService walletService;
    @Mock private MeterCommandService meterCommands;
    @Mock private SmsService smsService;

    @InjectMocks
    private TelemetryService telemetryService;

    @Test
    void shouldTriggerRelayCutWhenBalanceFallsBelowMinimum() {
        // Arrange
        String deviceId = "SIM-001";

        AppUser landlord = new AppUser();
        landlord.setId(1L);

        AppUser tenant = new AppUser();
        tenant.setId(10L);
        tenant.setPhone("9876543210");

        Room room = new Room();
        room.setLandlord(landlord);
        room.setTenant(tenant);

        Meter meter = new Meter();
        meter.setDeviceId(deviceId);
        meter.setRoom(room);
        meter.setRelayOn(true);
        meter.setActivatedAt(java.time.Instant.now());
        meter.setLastEnergyKwh(100.0);

        // Wallet with balance below the minimum limit (minBalance is 50.00, balance is 20.00)
        Wallet wallet = new Wallet();
        wallet.setBalance(new BigDecimal("20.00"));
        wallet.setMinBalance(new BigDecimal("50.00"));

        when(meters.findByDeviceIdWithRoomAndTenant(deviceId)).thenReturn(Optional.of(meter));
        when(landlordSettings.findByLandlordId(1L)).thenReturn(Optional.of(new LandlordSettings() {{
            setTariffPerKwh(new BigDecimal("8.00"));
        }}));
        // Usage delta will cost more than the remaining buffer, driving balance below minBalance
        when(walletService.apply(eq(10L), any(), any(), any())).thenReturn(wallet);

        // Incoming telemetry payload showing energy consumption
        TelemetryPayload payload = new TelemetryPayload(
                deviceId, 230.0, 5.0, 1000.0, 105.0, 50.0, 0.95, System.currentTimeMillis()
        );

        // Act
        telemetryService.handle(payload);

        // Assert: Verify that the backend command service sent a relay OFF signal
        verify(meterCommands).sendRelay(eq(meter), eq(false));
        verify(smsService).sendLowBalanceCut(eq(tenant), eq(wallet.getBalance()));
    }
}