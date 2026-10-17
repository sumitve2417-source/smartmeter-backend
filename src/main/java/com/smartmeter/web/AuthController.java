package com.smartmeter.web;

import com.smartmeter.dto.Dto.*;
import com.smartmeter.service.AuthService;
import com.smartmeter.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final PasswordResetService passwordReset;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    /** Always responds the same way whether or not the email is registered - never reveals which. */
    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordReset.requestReset(req.email());
        return Map.of("message", "If that email is registered, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordReset.reset(req.email(), req.token(), req.newPassword());
        return Map.of("message", "Password updated. You can now sign in.");
    }
}
