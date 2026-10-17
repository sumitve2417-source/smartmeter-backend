package com.smartmeter.service;

import com.smartmeter.domain.AppUser;
import com.smartmeter.repo.UserRepository;
import com.smartmeter.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserLookup {
    private final UserRepository users;

    public AppUser byUsername(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
