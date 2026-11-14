package com.smartmeter.service;

import com.smartmeter.domain.*;
import com.smartmeter.dto.Dto.RechargeResponse;
import com.smartmeter.dto.Dto.RechargeSubmit;
import com.smartmeter.repo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock private UserLookup lookup;
    @Mock private RoomRepository rooms;
    @Mock private WalletRepository wallets;
    @Mock private WalletTxnRepository txns;
    @Mock private LandlordSettingsRepository settingsRepo;
    @Mock private RechargeRequestRepository recharges;
    @Mock private WalletService walletService;
    @Mock private MeterCommandService meterCommands;

    @InjectMocks
    private TenantService tenantService;

    @Test
    void shouldAutomaticallyApproveAndCreditWalletOnRechargeSubmission() {
        // Arrange
        String username = "tenant@example.com";
        AppUser landlord = new AppUser();
        landlord.setId(1L);

        AppUser tenant = new AppUser();
        tenant.setId(10L);
        tenant.setFullName("Test Tenant");
        tenant.setLandlord(landlord);

        Room room = new Room();
        room.setName("Room 101");
        Meter meter = new Meter();
        meter.setRelayOn(false); // Power was cut
        room.setMeter(meter);

        Wallet wallet = new Wallet();
        wallet.setBalance(new BigDecimal("150.00"));
        wallet.setMinBalance(new BigDecimal("50.00"));

        RechargeSubmit submission = new RechargeSubmit(new BigDecimal("500.00"), "UPI123456789");

        when(lookup.byUsername(username)).thenReturn(tenant);
        when(recharges.existsByUtr("UPI123456789")).thenReturn(false);
        when(walletService.apply(eq(10L), eq(TxnType.RECHARGE), eq(submission.amount()), any()))
                .thenReturn(wallet);
        when(rooms.findByTenantId(10L)).thenReturn(Optional.of(room));

        // Act
        RechargeResponse response = tenantService.submitRecharge(username, submission);

        // Assert: Verify that it was saved directly as APPROVED and money was credited
        assertNotNull(response);
        assertEquals("APPROVED", response.status());
        assertEquals(new BigDecimal("500.00"), response.amount());

        // Verify that the wallet service was called to instantly add the money
        verify(walletService).apply(eq(10L), eq(TxnType.RECHARGE), eq(submission.amount()), any());

        // Verify that since the new balance (150) is greater than minBalance (50), power was restored automatically
        verify(meterCommands).sendRelay(eq(meter), eq(true));
    }
}