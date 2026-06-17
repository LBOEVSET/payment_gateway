package com.consoleshop.payment.hospital.controller;

import com.consoleshop.payment.dto.ApiResponse;
import com.consoleshop.payment.hospital.dto.InitiateCardRequest;
import com.consoleshop.payment.hospital.dto.InitiatePromptPayRequest;
import com.consoleshop.payment.hospital.service.HospitalPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Hospital Payment API — all under /api/v1/hospital-payments
 *
 * Console shop payments remain unchanged at /api/v1/payments.
 *
 * Auth: X-Internal-Secret (server-to-server from NestJS backend)
 *       + X-User-ID / X-User-Role injected by TrustedHeaderAuthFilter
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hospital-payments")
@RequiredArgsConstructor
public class HospitalPaymentController {

    private final HospitalPaymentService paymentService;

    /** POST /api/v1/hospital-payments/promptpay — create PromptPay charge, return QR URL */
    @PostMapping("/promptpay")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiatePromptPay(
            @Valid @RequestBody InitiatePromptPayRequest req,
            Authentication auth
    ) {
        String userId = (String) auth.getPrincipal();
        Map<String, Object> result = paymentService.initiatePromptPay(req.getBookingNumber(), userId);
        return ResponseEntity.ok(ApiResponse.ok("PromptPay charge created", result));
    }

    /** POST /api/v1/hospital-payments/card — card payment via Omise token */
    @PostMapping("/card")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiateCardPayment(
            @Valid @RequestBody InitiateCardRequest req,
            Authentication auth
    ) {
        String userId = (String) auth.getPrincipal();
        Map<String, Object> result = paymentService.initiateCardPayment(
            req.getBookingNumber(), req.getOmiseToken(), userId
        );
        return ResponseEntity.ok(ApiResponse.ok("Card payment processed", result));
    }

    /** GET /api/v1/hospital-payments/{bookingNumber} — check payment status */
    @GetMapping("/{bookingNumber}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentStatus(
            @PathVariable String bookingNumber,
            Authentication auth
    ) {
        String userId = (String) auth.getPrincipal();
        Map<String, Object> result = paymentService.getPaymentStatus(bookingNumber, userId);
        return ResponseEntity.ok(ApiResponse.ok("Payment status", result));
    }

    /**
     * POST /api/v1/hospital-payments/{bookingNumber}/sync
     * Called by NestJS on every poll while paymentStatus = PAYMENT_PENDING.
     * Fetches live charge status from Omise and updates records if changed.
     * This replaces the need for an Omise webhook.
     */
    @PostMapping("/{bookingNumber}/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncPaymentStatus(
            @PathVariable String bookingNumber,
            Authentication auth
    ) {
        String userId = (String) auth.getPrincipal();
        Map<String, Object> result = paymentService.syncPaymentStatus(bookingNumber, userId);
        return ResponseEntity.ok(ApiResponse.ok("Payment status synced", result));
    }

    /**
     * POST /api/v1/hospital-payments/webhook
     * Kept as an optional fallback — not required for normal operation.
     * Whitelisted in SecurityConfig (no X-Internal-Secret needed).
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "x-omise-signature", required = false) String signature
    ) {
        Map<String, Object> result = paymentService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok(result);
    }
}
