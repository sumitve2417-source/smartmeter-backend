package com.smartmeter.web;

import com.razorpay.Utils;
import com.smartmeter.domain.Meter;
import com.smartmeter.domain.RechargeRequest;
import com.smartmeter.domain.RechargeStatus;
import com.smartmeter.domain.Room;
import com.smartmeter.domain.TxnType;
import com.smartmeter.domain.Wallet;
import com.smartmeter.repo.RechargeRequestRepository;
import com.smartmeter.repo.RoomRepository;
import com.smartmeter.service.MeterCommandService;
import com.smartmeter.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final RechargeRequestRepository recharges;
    private final WalletService walletService;
    private final MeterCommandService meterCommands;
    private final RoomRepository rooms;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/razorpay")
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        try {
            // Verify cryptographic signature from the gateway
            boolean isValid = Utils.verifyWebhookSignature(payload, signature, webhookSecret);
            if (!isValid) {
                log.warn("Invalid Razorpay webhook signature received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
            }

            JSONObject event = new JSONObject(payload);
            if ("payment.captured".equals(event.getString("event"))) {
                JSONObject paymentEntity = event.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
                String orderId = paymentEntity.getString("order_id");
                String paymentId = paymentEntity.getString("id");

                RechargeRequest req = recharges.findByUtr(orderId).orElse(null);
                if (req != null && req.getStatus() == RechargeStatus.PENDING) {
                    req.setStatus(RechargeStatus.APPROVED);
                    req.setUtr(paymentId); // Update UTR with official gateway payment ID
                    req.setDecidedAt(Instant.now());
                    recharges.save(req);

                    // Safely credit only this tenant's room wallet
                    Long tenantId = req.getTenant().getId();
                    Wallet updatedWallet = walletService.apply(tenantId, TxnType.RECHARGE, req.getAmount(), "Auto UPI " + paymentId);

                    // Automatically restore relay power if it was cut due to low balance
                    rooms.findByTenantId(tenantId).ifPresent(room -> {
                        Meter m = room.getMeter();
                        if (!m.isRelayOn() && !m.isManualLock() && updatedWallet.getBalance().compareTo(updatedWallet.getMinBalance()) > 0) {
                            meterCommands.sendRelay(m, true);
                        }
                    });
                    log.info("Successfully processed payment and credited wallet for order: {}", orderId);
                }
            }
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error");
        }
    }
}