package com.smartmeter.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class Dto {
    private Dto() {}

    public record LoginRequest(@NotBlank @Size(max = 50) String username,
                               @NotBlank @Size(max = 72) String password) {}

    public record LoginResponse(String token, String role, String fullName, long expiresInSeconds) {}

    public record SettingsRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 4, fraction = 2) BigDecimal tariffPerKwh,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal minBalance,
            @NotBlank @Size(max = 100) String razorpayKeyId,
            @NotBlank @Size(max = 100) String razorpayKeySecret) {}

    public record SettingsResponse(BigDecimal tariffPerKwh, BigDecimal minBalance,
                                   String razorpayKeyId, String razorpayKeySecret) {}

    public record RoomRequest(@NotBlank @Size(max = 50) String name,
                              @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,40}$", message = "device id: 3-40 letters, digits, - or _") String deviceId) {}

    public record RoomResponse(Long id, String name, String deviceId, boolean relayOn, boolean manualLock,
                               Instant lastSeen, Long tenantId, String tenantName, BigDecimal balance,
                               Double currentPowerW, Double currentVoltage) {}

    public record OnboardRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "phone must be 10 digits") String phone,
            @NotBlank @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "username must be a valid email") String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Long roomId,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal openingBalance) {}

    public record TenantResponse(Long id, String fullName, String username, String phone, boolean active, String room) {}

    public record RelayRequest(@NotNull Boolean on) {}

    // ---- Payment Gateway Order DTOs ----
    public record PaymentOrderRequest(
            @NotNull @DecimalMin("1.00") @Digits(integer = 8, fraction = 2) BigDecimal amount
    ) {}

    public record PaymentOrderResponse(
            String orderId,
            BigDecimal amount,
            String keyId,
            String roomName
    ) {}
    // ------------------------------------

    public record RechargeResponse(Long id, String tenantName, String room, BigDecimal amount,
                                   String utr, String status, Instant createdAt) {}

    public record TenantDashboard(String fullName, String room, BigDecimal balance, BigDecimal minBalance,
                                  BigDecimal tariffPerKwh, boolean relayOn, boolean lowBalance,
                                  Double currentPowerW, Double currentVoltage, Instant lastSeen) {}

    public record PaymentInfo(String razorpayKeyId) {}

    public record TxnResponse(String type, BigDecimal amount, BigDecimal balanceAfter,
                              String reference, Instant createdAt) {}

    // ---- charts ----
    public record ChartPoint(String label, double kwh, BigDecimal revenue, Double avgPowerW) {}

    public record ChartResponse(String range, String period, List<ChartPoint> points,
                                double totalKwh, BigDecimal totalRevenue) {}

    // ---- monthly bill ----
    public record BillSummary(String period, String tenantName, String room, String landlordName,
                              BigDecimal oldBalance, BigDecimal recharged, double consumedKwh,
                              BigDecimal consumedAmount, BigDecimal tariffPerKwh, BigDecimal newBalance) {}

    // ---- account: password, email/phone, support ----
    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record ProfileResponse(String email, String phone, String fullName, String role) {}

    public record ProfileUpdateRequest(
            @NotBlank @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "invalid email") String email,
            @Pattern(regexp = "^[0-9]{10}$", message = "phone must be 10 digits") String phone,
            @NotBlank String currentPassword) {}

    /** email changes the login username, so a fresh token is required. */
    public record ProfileUpdateResponse(String token, String email, String phone, long expiresInSeconds) {}

    public record ForgotPasswordRequest(@NotBlank @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "invalid email") String email) {}

    public record ResetPasswordRequest(
            @NotBlank @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "invalid email") String email,
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record SupportInfo(String productMakerName, String productMakerEmail, String productMakerPhone,
                              String landlordName, String landlordEmail, String landlordPhone) {}

    // ---- admin: landlord management ----
    public record CreateLandlordRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "phone must be 10 digits") String phone,
            @NotBlank @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "username must be a valid email") String username,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record SetLandlordActiveRequest(@NotNull Boolean active) {}

    public record LandlordSummaryResponse(
            Long id,
            String fullName,
            String username,
            String phone,
            boolean active,
            int roomCount) {}
}

