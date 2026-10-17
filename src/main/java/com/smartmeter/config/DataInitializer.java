package com.smartmeter.config;

import com.smartmeter.domain.*;
import com.smartmeter.repo.LandlordSettingsRepository;
import com.smartmeter.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first landlord account when the database has none and a bootstrap password is configured. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final LandlordSettingsRepository settings;
    private final PasswordEncoder encoder;

    @Value("${app.bootstrap.landlord-username}") private String username;
    @Value("${app.bootstrap.landlord-password}") private String password;
    @Value("${app.bootstrap.landlord-name}") private String fullName;

    @Override
    @Transactional
    public void run(String... args) {
        if (users.existsByRole(Role.LANDLORD)) return;
        if (password == null || password.length() < 8) {
            log.warn("No landlord exists. Set BOOT_LANDLORD_PASS (min 8 chars) and restart to create the first landlord.");
            return;
        }
        AppUser l = new AppUser();
        l.setUsername(username);
        l.setFullName(fullName);
        l.setRole(Role.LANDLORD);
        l.setPasswordHash(encoder.encode(password));
        users.save(l);
        LandlordSettings s = new LandlordSettings();
        s.setLandlord(l);
        settings.save(s);
        log.info("Created first landlord '{}'", username);
    }
}
