package com.smartmeter.web;

import com.smartmeter.dto.Dto.*;
import com.smartmeter.service.BillService;
import com.smartmeter.service.ConsumptionService;
import com.smartmeter.service.SupportService;
import com.smartmeter.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Requires ROLE_TENANT. A tenant can only ever reach their own wallet, room and landlord's payment details. */
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {
    private final TenantService svc;
    private final ConsumptionService consumption;
    private final BillService bills;
    private final SupportService support;

    @GetMapping("/dashboard")
    public TenantDashboard dashboard(Authentication a) { return svc.dashboard(a.getName()); }

    @GetMapping("/payment-info")
    public PaymentInfo paymentInfo(Authentication a) { return svc.paymentInfo(a.getName()); }

    @PostMapping("/recharges")
    @ResponseStatus(HttpStatus.CREATED)
    public RechargeResponse recharge(Authentication a, @Valid @RequestBody RechargeSubmit r) {
        return svc.submitRecharge(a.getName(), r);
    }

    @GetMapping("/recharges")
    public List<RechargeResponse> recharges(Authentication a) { return svc.myRecharges(a.getName()); }

    @GetMapping("/transactions")
    public List<TxnResponse> transactions(Authentication a) { return svc.transactions(a.getName()); }

    /** range = day | month | year. date = YYYY-MM-DD / YYYY-MM / YYYY matching range; omit for "now". */
    @GetMapping("/consumption")
    public ChartResponse consumption(Authentication a, @RequestParam(defaultValue = "month") String range,
                                     @RequestParam(required = false) String date) {
        return consumption.tenantChart(a.getName(), range, date);
    }

    @GetMapping("/support")
    public SupportInfo support(Authentication a) { return support.forTenant(a.getName()); }

    @GetMapping("/bills")
    public List<String> billPeriods() { return bills.availablePeriods(); }

    @GetMapping("/bills/{period}")
    public BillSummary bill(Authentication a, @PathVariable String period) { return bills.tenantBill(a.getName(), period); }

    @GetMapping("/bills/{period}/pdf")
    public ResponseEntity<byte[]> billPdf(Authentication a, @PathVariable String period) {
        byte[] pdf = bills.renderPdf(bills.tenantBill(a.getName(), period));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bill-" + period + ".pdf\"")
                .body(pdf);
    }
}
