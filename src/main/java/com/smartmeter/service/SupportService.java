package com.smartmeter.service;

import com.smartmeter.config.SupportProperties;
import com.smartmeter.domain.AppUser;
import com.smartmeter.dto.Dto.SupportInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportService {
    private final UserLookup lookup;
    private final SupportProperties support;

    /** Landlord's Customer Care tab: only the product maker's contact (their own app's support line). */
    public SupportInfo forLandlord() {
        return new SupportInfo(support.getProductMakerName(), support.getProductMakerEmail(), support.getProductMakerPhone(),
                null, null, null);
    }

    /** Tenant's Customer Care tab: their landlord's contact, plus the product maker's contact. */
    @Transactional(readOnly = true)
    public SupportInfo forTenant(String username) {
        AppUser tenant = lookup.byUsername(username);
        AppUser landlord = tenant.getLandlord();
        return new SupportInfo(support.getProductMakerName(), support.getProductMakerEmail(), support.getProductMakerPhone(),
                landlord != null ? landlord.getFullName() : null,
                landlord != null ? landlord.getUsername() : null,
                landlord != null ? landlord.getPhone() : null);
    }
}
