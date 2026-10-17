package com.smartmeter.service;

import com.smartmeter.domain.AppUser;
import com.smartmeter.repo.UserRepository;
import com.smartmeter.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Email-based "forgot password" flow. Only a SHA-256 hash of the reset token is ever stored - the raw
 * token exists only in the emailed link, exactly like a real password-reset link should work. Always
 * responds the same way whether or not the email is registered, so no one can probe which emails exist.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final EmailService email;

    @Value("${app.frontend-url}") private String frontendUrl;
    @Value("${app.password-reset.expiry-minutes}") private long expiryMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public void requestReset(String rawEmail) {
        String emailAddr = rawEmail.trim().toLowerCase();
        users.findByUsername(emailAddr).ifPresent(user -> {
            String token = generateToken();
            user.setResetTokenHash(hash(token));
            user.setResetTokenExpiry(Instant.now().plus(Duration.ofMinutes(expiryMinutes)));
            users.save(user);

            String link = frontendUrl + "/reset-password?email=" + urlEncode(emailAddr) + "&token=" + token;
            email.send(emailAddr, "Reset your SmartMeter password",
                    "Hi " + user.getFullName() + ",\n\n" +
                    "We received a request to reset your SmartMeter password. This link expires in " +
                    expiryMinutes + " minutes:\n\n" + link + "\n\n" +
                    "If you didn't request this, you can safely ignore this email.");
        });
        // Same response whether the email exists or not - nothing here reveals which.
    }

    @Transactional
    public void reset(String rawEmail, String token, String newPassword) {
        String emailAddr = rawEmail.trim().toLowerCase();
        AppUser user = users.findByUsername(emailAddr)
                .filter(u -> u.getResetTokenHash() != null && u.getResetTokenExpiry() != null)
                .filter(u -> u.getResetTokenExpiry().isAfter(Instant.now()))
                .filter(u -> constantTimeEquals(u.getResetTokenHash(), hash(token)))
                .orElseThrow(() -> ApiException.bad("This reset link is invalid or has expired. Request a new one."));

        user.setPasswordHash(encoder.encode(newPassword));
        user.setResetTokenHash(null);
        user.setResetTokenExpiry(null);
        users.save(user);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
