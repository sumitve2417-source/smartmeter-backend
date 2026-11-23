package com.smartmeter.web;

import com.smartmeter.dto.Dto.CreateLandlordRequest;
import com.smartmeter.dto.Dto.LandlordSummaryResponse;
import com.smartmeter.dto.Dto.SetLandlordActiveRequest;
import com.smartmeter.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @PostMapping("/landlords")
    @ResponseStatus(HttpStatus.CREATED)
    public LandlordSummaryResponse createLandlord(@Valid @RequestBody CreateLandlordRequest req) {
        return adminService.createLandlord(req);
    }

    @GetMapping("/landlords")
    public List<LandlordSummaryResponse> listLandlords() {
        return adminService.listLandlords();
    }

    @PatchMapping("/landlords/{id}/active")
    public LandlordSummaryResponse setLandlordActive(@PathVariable Long id,
                                                     @Valid @RequestBody SetLandlordActiveRequest req) {
        return adminService.setActive(id, req.active());
    }
}
