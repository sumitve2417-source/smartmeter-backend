package com.smartmeter.service;

import com.smartmeter.domain.AppUser;
import com.smartmeter.dto.Dto.LoginRequest;
import com.smartmeter.dto.Dto.LoginResponse;
import com.smartmeter.repo.UserRepository;
import com.smartmeter.security.JwtService;
import com.smartmeter.web.ApiException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginThrottle throttle;

    // Used to burn the same CPU time when the username does not exist (prevents user enumeration by timing).
    private String dummyHash;

    @PostConstruct
    void init() {
        dummyHash = encoder.encode("timing-equalizer");
    }

    public LoginResponse login(LoginRequest req) {
        String name = req.username().trim().toLowerCase();
        if (throttle.isLocked(name)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in 15 minutes.");
        }
        AppUser user = users.findByUsername(name).orElse(null);
        boolean ok = encoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok || !user.isActive()) {
            throttle.recordFailure(name);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        throttle.recordSuccess(name);
        return new LoginResponse(jwt.generate(user), user.getRole().name(), user.getFullName(), jwt.expirySeconds());
    }
}
