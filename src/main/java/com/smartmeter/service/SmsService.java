package com.smartmeter.service;

import com.smartmeter.domain.AppUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SMS placeholder: logs instead of sending, and rate-limits one warning/cut message per tenant per 30 minutes
 * so a tenant hovering near the threshold doesn't get spammed one SMS per telemetry sample.
 * Swap sendNow() for a real provider (Twilio, MSG91, Fast2SMS...) when you have credentials.
 */
@Slf4j
@Service
public class SmsService {
    private final ConcurrentHashMap<String, Instant> lastSentAt = new ConcurrentHashMap<>();

    public void sendLowBalanceWarning(AppUser tenant, BigDecimal balance) {
        sendThrottled(tenant, "warn", "Your electricity wallet balance is low: Rs " + balance + ". Please recharge soon.");
    }

    public void sendLowBalanceCut(AppUser tenant, BigDecimal balance) {
        sendThrottled(tenant, "cut", "Your electricity has been cut - wallet balance Rs " + balance + " is below the minimum. Recharge to restore power.");
    }

    private void sendThrottled(AppUser tenant, String kind, String message) {
        String key = tenant.getId() + ":" + kind;
        Instant last = lastSentAt.get(key);
        if (last != null && last.isAfter(Instant.now().minus(30, ChronoUnit.MINUTES))) return;
        lastSentAt.put(key, Instant.now());
        sendNow(tenant.getPhone(), message);
    }

    private void sendNow(String phone, String message) {
        log.info("[SMS to {}] {}", phone, message);
    }
}
