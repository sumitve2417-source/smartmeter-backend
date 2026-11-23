package com.smartmeter.service;

import com.smartmeter.domain.AppUser;
import com.smartmeter.domain.LandlordSettings;
import com.smartmeter.domain.Role;
import com.smartmeter.dto.Dto.CreateLandlordRequest;
import com.smartmeter.dto.Dto.LandlordSummaryResponse;
import com.smartmeter.repo.LandlordSettingsRepository;
import com.smartmeter.repo.RoomRepository;
import com.smartmeter.repo.UserRepository;
import com.smartmeter.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository users;
    private final RoomRepository rooms;
    private final LandlordSettingsRepository settingsRepo;
    private final PasswordEncoder encoder;

    @Transactional
    public LandlordSummaryResponse createLandlord(CreateLandlordRequest req) {
        String email = req.username().trim().toLowerCase();
        if (users.existsByUsername(email)) {
            throw ApiException.conflict("Email already registered");
        }

        AppUser landlord = new AppUser();
        landlord.setUsername(email);
        landlord.setPasswordHash(encoder.encode(req.password()));
        landlord.setFullName(req.fullName().trim());
        landlord.setPhone(req.phone().trim());
        landlord.setRole(Role.LANDLORD);
        landlord.setActive(true);
        users.save(landlord);

        // Initialize default LandlordSettings just like DataInitializer does for the first landlord[cite: 1]
        LandlordSettings settings = new LandlordSettings();
        settings.setLandlord(landlord);
        settingsRepo.save(settings);

        return toSummary(landlord);
    }

    @Transactional(readOnly = true)
    public List<LandlordSummaryResponse> listLandlords() {
        return users.findAllByRoleOrderByFullNameAsc(Role.LANDLORD)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public LandlordSummaryResponse setActive(Long landlordId, boolean active) {
        AppUser landlord = findLandlordById(landlordId);
        landlord.setActive(active);
        users.save(landlord);
        return toSummary(landlord);
    }

    private AppUser findLandlordById(Long landlordId) {
        AppUser user = users.findById(landlordId)
                .orElseThrow(() -> ApiException.notFound("Landlord not found"));
        if (user.getRole() != Role.LANDLORD) {
            throw ApiException.bad("Target user is not a landlord");
        }
        return user;
    }

    private LandlordSummaryResponse toSummary(AppUser landlord) {
        int roomCount = rooms.findByLandlordIdOrderByName(landlord.getId()).size();
        return new LandlordSummaryResponse(
                landlord.getId(),
                landlord.getFullName(),
                landlord.getUsername(),
                landlord.getPhone(),
                landlord.isActive(),
                roomCount
        );
    }
}
