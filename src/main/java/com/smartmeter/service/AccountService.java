package com.smartmeter.service;

import com.smartmeter.domain.AppUser;
import com.smartmeter.dto.Dto.*;
import com.smartmeter.repo.UserRepository;
import com.smartmeter.security.JwtService;
import com.smartmeter.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service account settings, shared by both roles: view profile, change email/phone, change password. */
@Service
@RequiredArgsConstructor
public class AccountService {
    private final UserLookup lookup;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    @Transactional(readOnly = true)
    public ProfileResponse me(String username) {
        AppUser u = lookup.byUsername(username);
        return new ProfileResponse(u.getUsername(), u.getPhone(), u.getFullName(), u.getRole().name());
    }

    @Transactional
    public ProfileUpdateResponse updateProfile(String username, ProfileUpdateRequest r) {
        AppUser u = lookup.byUsername(username);
        if (!encoder.matches(r.currentPassword(), u.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        String newEmail = r.email().trim().toLowerCase();

        if (!newEmail.equals(u.getUsername()) && users.existsByUsernameAndIdNot(newEmail, u.getId())) {
            throw ApiException.conflict("That email is already registered to another account");
        }
        u.setUsername(newEmail);
        if (r.phone() != null && !r.phone().isBlank()) u.setPhone(r.phone().trim());
        users.save(u);

        // Email is the JWT subject, so the old token is now stale - issue a fresh one in the same response.
        String token = jwt.generate(u);
        return new ProfileUpdateResponse(token, u.getUsername(), u.getPhone(), jwt.expirySeconds());
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest r) {
        AppUser u = lookup.byUsername(username);
        if (!encoder.matches(r.currentPassword(), u.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        u.setPasswordHash(encoder.encode(r.newPassword()));
        users.save(u);
    }
}
