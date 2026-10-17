package com.smartmeter.web;

import com.smartmeter.domain.RechargeStatus;
import com.smartmeter.dto.Dto.*;
import com.smartmeter.service.BillService;
import com.smartmeter.service.ConsumptionService;
import com.smartmeter.service.LandlordService;
import com.smartmeter.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Every route here requires ROLE_LANDLORD (see SecurityConfig); every query is scoped to the caller's own data. */
@RestController
@RequestMapping("/api/landlord")
@RequiredArgsConstructor
public class LandlordController {
    private final LandlordService svc;
    private final ConsumptionService consumptionSvc;
    private final BillService bills;
    private final SupportService support;

    @GetMapping("/settings")
    public SettingsResponse settings(Authentication a) { return svc.getSettings(a.getName()); }

    @PutMapping("/settings")
    public SettingsResponse updateSettings(Authentication a, @Valid @RequestBody SettingsRequest r) {
        return svc.updateSettings(a.getName(), r);
    }

    @PostMapping(value = "/settings/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SettingsResponse uploadQr(Authentication a, @RequestParam("file") MultipartFile file) {
        return svc.uploadQr(a.getName(), file);
    }

    @GetMapping("/rooms")
    public List<RoomResponse> rooms(Authentication a) { return svc.listRooms(a.getName()); }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse addRoom(Authentication a, @Valid @RequestBody RoomRequest r) { return svc.addRoom(a.getName(), r); }

    @PostMapping("/rooms/{id}/relay")
    public RoomResponse relay(Authentication a, @PathVariable Long id, @Valid @RequestBody RelayRequest r) {
        return svc.setRelay(a.getName(), id, r.on());
    }

    @PostMapping("/rooms/{id}/vacate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vacate(Authentication a, @PathVariable Long id) { svc.vacateRoom(a.getName(), id); }

    @GetMapping("/tenants")
    public List<TenantResponse> tenants(Authentication a) { return svc.listTenants(a.getName()); }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse onboard(Authentication a, @Valid @RequestBody OnboardRequest r) {
        return svc.onboardTenant(a.getName(), r);
    }

    @GetMapping("/recharges")
    public List<RechargeResponse> recharges(Authentication a, @RequestParam(defaultValue = "PENDING") RechargeStatus status) {
        return svc.listRecharges(a.getName(), status);
    }

    @PostMapping("/recharges/{id}/approve")
    public RechargeResponse approve(Authentication a, @PathVariable Long id) { return svc.approveRecharge(a.getName(), id); }

    @PostMapping("/recharges/{id}/reject")
    public RechargeResponse reject(Authentication a, @PathVariable Long id) { return svc.rejectRecharge(a.getName(), id); }

    @GetMapping("/support")
    public SupportInfo support() { return support.forLandlord(); }

    /** range = day | month | year. Omit roomId for the landlord's total across every room. */
    @GetMapping("/analytics")
    public ChartResponse analytics(Authentication a, @RequestParam(defaultValue = "month") String range,
                                   @RequestParam(required = false) String date, @RequestParam(required = false) Long roomId) {
        return consumptionSvc.landlordChart(a.getName(), range, date, roomId);
    }

    @GetMapping("/rooms/{id}/bills/{period}")
    public BillSummary roomBill(Authentication a, @PathVariable Long id, @PathVariable String period) {
        return bills.landlordViewOfBill(a.getName(), id, period);
    }

    @GetMapping("/rooms/{id}/bills/{period}/pdf")
    public ResponseEntity<byte[]> roomBillPdf(Authentication a, @PathVariable Long id, @PathVariable String period) {
        byte[] pdf = bills.renderPdf(bills.landlordViewOfBill(a.getName(), id, period));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bill-" + period + ".pdf\"")
                .body(pdf);
    }
}
