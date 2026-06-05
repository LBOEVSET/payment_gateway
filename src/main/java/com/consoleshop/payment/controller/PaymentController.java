package com.consoleshop.payment.controller;

import com.consoleshop.payment.dto.ApiResponse;
import com.consoleshop.payment.dto.PayWithCardRequest;
import com.consoleshop.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Payment endpoints, all under /api/v1/payments.
 * Authentication context is provided by TrustedHeaderAuthFilter via X-User-ID header.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/v1/payments/card
     * Pay for an order with a credit/debit card token.
     */
    @PostMapping("/card")
    public ResponseEntity<ApiResponse<Map<String, Object>>> payWithCard(
            @Valid @RequestBody PayWithCardRequest request,
            Authentication auth
    ) {
        String userId = (String) auth.getPrincipal();
        Map<String, Object> result = paymentService.payWithCard(
                request.getOrderId(),
                request.getToken(),
                userId
        );
        return ResponseEntity.ok(ApiResponse.ok("Payment successful", result));
    }

    /**
     * POST /api/v1/payments/promptpay
     * Initiate a PromptPay charge for an order.
     */
    @PostMapping("/promptpay")
    public ResponseEntity<ApiResponse<Map<String, Object>>> payWithPromptPay(
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        String userId = (String) auth.getPrincipal();
        String orderId = body.get("orderId");

        if (orderId == null || orderId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "orderId is required"));
        }

        Map<String, Object> result = paymentService.payWithPromptPay(orderId, userId);
        return ResponseEntity.ok(ApiResponse.ok("PromptPay charge created", result));
    }

    /**
     * POST /api/v1/payments/webhook
     * Omise webhook — no auth required, signature verified in service.
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
