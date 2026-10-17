package com.smartmeter.web;

import com.smartmeter.dto.Dto.*;
import com.smartmeter.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Requires ROLE_LANDLORD or ROLE_TENANT (see SecurityConfig) - a shared "Security" settings page for both roles. */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService svc;

    @GetMapping("/me")
    public ProfileResponse me(Authentication a) { return svc.me(a.getName()); }

    @PutMapping("/profile")
    public ProfileUpdateResponse updateProfile(Authentication a, @Valid @RequestBody ProfileUpdateRequest r) {
        return svc.updateProfile(a.getName(), r);
    }

    @PutMapping("/password")
    public void changePassword(Authentication a, @Valid @RequestBody ChangePasswordRequest r) {
        svc.changePassword(a.getName(), r);
    }
}
