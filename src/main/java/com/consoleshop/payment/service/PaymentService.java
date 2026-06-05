package com.consoleshop.payment.service;

import com.consoleshop.payment.config.OmiseConfig;
import com.consoleshop.payment.domain.entity.OrderItem;
import com.consoleshop.payment.domain.entity.Payment;
import com.consoleshop.payment.domain.enums.OrderStatus;
import com.consoleshop.payment.domain.enums.PaymentStatus;
import com.consoleshop.payment.exception.PaymentException;
import com.consoleshop.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final EventRepository eventRepository;
    private final OmiseService omiseService;
    private final OmiseConfig omiseConfig;

    // ─── Credit Card Payment ──────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> payWithCard(String orderId, String token, String userId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> PaymentException.notFound("Payment not found"));

        if (!payment.getOrder().getUserId().equals(userId)) {
            throw PaymentException.forbidden("Access denied");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw PaymentException.badRequest("Payment already processed");
        }

        // Omise minimum charge is ฿20 (2000 satangs). Validate early so we
        // return a clear message instead of letting Omise reject it.
        if (payment.getAmount().doubleValue() < 20.0) {
            throw PaymentException.badRequest(
                "Minimum payment amount is ฿20. Current total: ฿" + payment.getAmount().toPlainString()
            );
        }

        Map<String, Object> charge = omiseService.createCardCharge(
                payment.getAmount().doubleValue(),
                token,
                orderId
        );

        Boolean paid = (Boolean) charge.get("paid");
        if (paid == null || !paid) {
            throw PaymentException.badRequest("Card payment failed");
        }

        String chargeId = (String) charge.get("id");

        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setChargeId(chargeId);
        payment.setRawPayload(charge);
        paymentRepository.save(payment);

        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
        });

        log.info("Card payment successful: orderId={} chargeId={}", orderId, chargeId);
        return Map.of("success", true, "chargeId", chargeId);
    }

    // ─── PromptPay Payment ────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> payWithPromptPay(String orderId, String userId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> PaymentException.notFound("Payment not found"));

        if (!payment.getOrder().getUserId().equals(userId)) {
            throw PaymentException.forbidden("Access denied");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw PaymentException.badRequest("Payment already processed");
        }

        // Omise minimum charge is ฿20 (2000 satangs). Validate early.
        if (payment.getAmount().doubleValue() < 20.0) {
            throw PaymentException.badRequest(
                "Minimum payment amount is ฿20. Current total: ฿" + payment.getAmount().toPlainString()
            );
        }

        Map<String, Object> charge = omiseService.createPromptPayCharge(
                payment.getAmount().doubleValue(),
                orderId
        );

        String chargeId = (String) charge.get("id");

        payment.setChargeId(chargeId);
        payment.setRawPayload(charge);
        paymentRepository.save(payment);

        // Extract QR code URL from the nested Omise response structure:
        // charge.source.scannable_code.image.download_uri
        String qrCode = extractQrCode(charge);

        log.info("PromptPay charge created: orderId={} chargeId={}", orderId, chargeId);
        return Map.of("qrCode", qrCode != null ? qrCode : "", "chargeId", chargeId);
    }

    @SuppressWarnings("unchecked")
    private String extractQrCode(Map<String, Object> charge) {
        try {
            Map<String, Object> source = (Map<String, Object>) charge.get("source");
            if (source == null) return null;
            Map<String, Object> scannableCode = (Map<String, Object>) source.get("scannable_code");
            if (scannableCode == null) return null;
            Map<String, Object> image = (Map<String, Object>) scannableCode.get("image");
            if (image == null) return null;
            return (String) image.get("download_uri");
        } catch (ClassCastException e) {
            log.warn("Could not extract QR code from Omise charge response", e);
            return null;
        }
    }

    // ─── Omise Webhook ────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> handleWebhook(byte[] rawBody, String signature) {
        verifySignature(rawBody, signature);

        Map<String, Object> body = parseJson(rawBody);
        @SuppressWarnings("unchecked")
        Map<String, Object> charge = (Map<String, Object>) body.get("data");

        if (charge == null || charge.get("id") == null) {
            return Map.of("received", true);
        }

        String chargeId = (String) charge.get("id");

        Payment payment = paymentRepository.findByChargeId(chargeId)
                .orElse(null);

        if (payment == null) {
            log.warn("Webhook received for unknown chargeId={}", chargeId);
            return Map.of("received", true);
        }

        if (payment.getStatus() == PaymentStatus.SUCCESSFUL ||
                payment.getStatus() == PaymentStatus.FAILED) {
            return Map.of("received", true);
        }

        String chargeStatus = (String) charge.get("status");

        if ("successful".equals(chargeStatus)) {
            payment.setStatus(PaymentStatus.SUCCESSFUL);
            payment.setRawPayload(body);
            paymentRepository.save(payment);

            orderRepository.findById(payment.getOrderId()).ifPresent(order -> {
                order.setStatus(OrderStatus.PAID);
                orderRepository.save(order);
            });

            log.info("Webhook: payment successful chargeId={}", chargeId);

        } else if ("failed".equals(chargeStatus)) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setRawPayload(body);
            paymentRepository.save(payment);

            orderRepository.findById(payment.getOrderId()).ifPresent(order -> {
                order.setStatus(OrderStatus.FAILED);
                orderRepository.save(order);
            });

            restoreStock(payment.getOrderId());
            log.info("Webhook: payment failed chargeId={} — stock restored", chargeId);
        }

        return Map.of("received", true);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void restoreStock(String orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            if (item.getProductId() != null) {
                productRepository.incrementStock(item.getProductId(), item.getQuantity());
            } else if (item.getEventId() != null) {
                eventRepository.incrementStock(item.getEventId(), item.getQuantity());
            }
        }
    }

    private void verifySignature(byte[] rawBody, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    omiseConfig.getWebhookSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            byte[] digest = mac.doFinal(rawBody);
            String expected = HexFormat.of().formatHex(digest);

            if (!expected.equals(signature)) {
                throw PaymentException.badRequest("Invalid webhook signature");
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Signature verification failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(byte[] rawBody) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(rawBody, Map.class);
        } catch (Exception e) {
            throw PaymentException.badRequest("Invalid JSON body");
        }
    }
}
