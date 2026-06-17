package com.consoleshop.payment.hospital.service;

import com.consoleshop.payment.config.OmiseConfig;
import com.consoleshop.payment.exception.PaymentException;
import com.consoleshop.payment.hospital.domain.mongo.HospitalBookingDocument;
import com.consoleshop.payment.hospital.domain.postgres.HospitalPaymentRecord;
import com.consoleshop.payment.hospital.repository.HospitalBookingRepository;
import com.consoleshop.payment.hospital.repository.HospitalPaymentRecordRepository;
import com.consoleshop.payment.service.OmiseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalPaymentService {

    private final HospitalBookingRepository       bookingRepository;
    private final HospitalPaymentRecordRepository paymentRecordRepository;
    private final MongoTemplate                   mongoTemplate;
    private final OmiseService                    omiseService;
    private final OmiseConfig                     omiseConfig;

    @Value("${app.hospital-backend-url}")
    private String hospitalBackendUrl;

    @Value("${app.hospital-backend-secret}")
    private String hospitalBackendSecret;

    // ─── Initiate PromptPay ───────────────────────────────────────────────────

    public Map<String, Object> initiatePromptPay(String bookingNumber, String userId) {
        HospitalBookingDocument booking = findAndValidate(bookingNumber, userId);

        Map<String, Object> charge = omiseService.createPromptPayCharge(
            booking.getFinalAmount().doubleValue(), bookingNumber, "bangkokhospital", "bookingNumber"
        );

        String chargeId = (String) charge.get("id");
        String qrUrl    = extractQrUrl(charge);

        updateBookingMongo(booking.getId(), chargeId, qrUrl, "PAYMENT_PENDING");
        upsertRecord(booking, chargeId, "PROMPTPAY", "PAYMENT_PENDING");

        log.info("[Hospital] PromptPay initiated: bookingNumber={} chargeId={}", bookingNumber, chargeId);

        return Map.of(
            "bookingNumber", bookingNumber,
            "chargeId",      chargeId,
            "qrCodeUrl",     qrUrl != null ? qrUrl : "",
            "amount",        booking.getFinalAmount()
        );
    }

    // ─── Initiate Card Payment ────────────────────────────────────────────────

    public Map<String, Object> initiateCardPayment(String bookingNumber, String omiseToken, String userId) {
        HospitalBookingDocument booking = findAndValidate(bookingNumber, userId);

        Map<String, Object> charge  = omiseService.createCardCharge(
            booking.getFinalAmount().doubleValue(), omiseToken, bookingNumber, "bangkokhospital", "bookingNumber"
        );

        String  chargeId = (String)  charge.get("id");
        Boolean paid     = (Boolean) charge.get("paid");

        if (Boolean.TRUE.equals(paid)) {
            updateBookingMongo(booking.getId(), chargeId, null, "PAID");
            updateBookingStatus(booking.getId(), "CONFIRMED");
            upsertRecord(booking, chargeId, "CARD", "PAID");
            notifyBackend(bookingNumber, "SUCCESS", chargeId);
            log.info("[Hospital] Card payment successful: bookingNumber={} chargeId={}", bookingNumber, chargeId);
            return Map.of("bookingNumber", bookingNumber, "chargeId", chargeId, "paid", true);
        }

        // 3DS redirect
        String authorizeUrl = (String) charge.getOrDefault("authorize_uri", "");
        updateBookingMongo(booking.getId(), chargeId, null, "PAYMENT_PENDING");
        upsertRecord(booking, chargeId, "CARD", "PAYMENT_PENDING");

        return Map.of(
            "bookingNumber", bookingNumber,
            "chargeId",      chargeId,
            "paid",          false,
            "authorizeUrl",  authorizeUrl
        );
    }

    // ─── Sync Payment Status (polling alternative to webhook) ─────────────────

    /**
     * Fetches the live Omise charge status and updates MongoDB + PostgreSQL if the
     * charge has moved to successful/failed. Called by NestJS on every client poll
     * while paymentStatus = PAYMENT_PENDING — no Omise webhook needed.
     */
    public Map<String, Object> syncPaymentStatus(String bookingNumber, String userId) {
        HospitalBookingDocument booking = bookingRepository.findByBookingNumber(bookingNumber)
            .orElseThrow(() -> PaymentException.notFound("Booking not found"));

        if (!booking.getUserId().equals(userId)) throw PaymentException.forbidden("Access denied");

        String chargeId = booking.getPaymentTransactionId();
        if (chargeId == null || chargeId.isBlank()) {
            return Map.of("bookingNumber", bookingNumber, "paymentStatus", "PENDING");
        }

        // Already in a terminal state — nothing to sync
        String current = booking.getPaymentStatus();
        if ("PAID".equals(current) || "FAILED".equals(current)) {
            return Map.of("bookingNumber", bookingNumber, "paymentStatus", current);
        }

        // Ask Omise for the live status
        @SuppressWarnings("unchecked")
        Map<String, Object> charge = omiseService.getCharge(chargeId);
        String omiseStatus = (String) charge.get("status"); // pending | successful | failed

        if ("successful".equals(omiseStatus)) {
            // Update PostgreSQL audit record first (our own data)
            updateRecordStatus(chargeId, "PAID", "successful", null);
            // Notify NestJS synchronously — it owns MongoDB and handles rewards/audit
            notifyBackendSync(bookingNumber, "SUCCESS", chargeId);
            log.info("[Hospital] Sync: charge successful bookingNumber={} chargeId={}", bookingNumber, chargeId);
            return Map.of("bookingNumber", bookingNumber, "paymentStatus", "PAID");

        } else if ("failed".equals(omiseStatus)) {
            String reason = (String) charge.getOrDefault("failure_message", "Payment failed");
            updateRecordStatus(chargeId, "FAILED", "failed", reason);
            notifyBackendSync(bookingNumber, "FAILED", chargeId);
            log.warn("[Hospital] Sync: charge failed bookingNumber={} reason={}", bookingNumber, reason);
            return Map.of("bookingNumber", bookingNumber, "paymentStatus", "FAILED");
        }

        // Still pending — no change
        return Map.of("bookingNumber", bookingNumber, "paymentStatus", current != null ? current : "PAYMENT_PENDING");
    }

    // ─── Payment Status ───────────────────────────────────────────────────────

    public Map<String, Object> getPaymentStatus(String bookingNumber, String userId) {
        HospitalBookingDocument booking = bookingRepository.findByBookingNumber(bookingNumber)
            .orElseThrow(() -> PaymentException.notFound("Booking not found"));

        if (!booking.getUserId().equals(userId)) throw PaymentException.forbidden("Access denied");

        return Map.of(
            "bookingNumber", bookingNumber,
            "paymentStatus", booking.getPaymentStatus() != null ? booking.getPaymentStatus() : "PENDING",
            "chargeId",      booking.getPaymentTransactionId() != null ? booking.getPaymentTransactionId() : "",
            "qrCodeUrl",     booking.getQrCodeUrl() != null ? booking.getQrCodeUrl() : "",
            "paidAt",        booking.getPaidAt() != null ? booking.getPaidAt().toString() : ""
        );
    }

    // ─── Omise Webhook ────────────────────────────────────────────────────────

    public Map<String, Object> handleWebhook(byte[] rawBody, String signature) {
        verifySignature(rawBody, signature);

        Map<String, Object> body = parseJson(rawBody);
        if (!"event".equals(body.get("object"))) return Map.of("received", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        if (data == null) return Map.of("received", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        if (metadata == null || !"bangkokhospital".equals(metadata.get("provider"))) {
            return Map.of("received", true);
        }

        String chargeId      = (String) data.get("id");
        String chargeStatus  = (String) data.get("status");
        String bookingNumber = (String) metadata.get("bookingNumber");

        if (chargeId == null || bookingNumber == null) return Map.of("received", true);

        HospitalBookingDocument booking = bookingRepository.findByBookingNumber(bookingNumber).orElse(null);
        if (booking == null) {
            log.warn("[Hospital] Webhook: booking not found bookingNumber={}", bookingNumber);
            return Map.of("received", true);
        }

        // Idempotency
        if ("PAID".equals(booking.getPaymentStatus()) || "FAILED".equals(booking.getPaymentStatus())) {
            return Map.of("received", true);
        }

        if ("successful".equals(chargeStatus)) {
            updateBookingMongo(booking.getId(), chargeId, null, "PAID");
            updateBookingStatus(booking.getId(), "CONFIRMED");
            updateRecordStatus(chargeId, "PAID", "successful", null);
            notifyBackend(bookingNumber, "SUCCESS", chargeId);
            log.info("[Hospital] Webhook: paid bookingNumber={} chargeId={}", bookingNumber, chargeId);

        } else if ("failed".equals(chargeStatus)) {
            String reason = (String) data.getOrDefault("failure_message", "Payment failed");
            updateBookingMongo(booking.getId(), chargeId, null, "FAILED");
            updateRecordStatus(chargeId, "FAILED", "failed", reason);
            notifyBackend(bookingNumber, "FAILED", chargeId);
            log.warn("[Hospital] Webhook: failed bookingNumber={} reason={}", bookingNumber, reason);
        }

        return Map.of("received", true);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private HospitalBookingDocument findAndValidate(String bookingNumber, String userId) {
        HospitalBookingDocument b = bookingRepository.findByBookingNumber(bookingNumber)
            .orElseThrow(() -> PaymentException.notFound("Booking not found: " + bookingNumber));

        if (!b.getUserId().equals(userId))         throw PaymentException.forbidden("Access denied");
        if (!"ONLINE".equals(b.getPaymentMethod())) throw PaymentException.badRequest("Not an online payment booking");
        if ("PAID".equals(b.getPaymentStatus()))   throw PaymentException.conflict("Already paid");
        if ("CANCELLED".equals(b.getStatus()))     throw PaymentException.badRequest("Booking is cancelled");
        if (b.getFinalAmount() == null || b.getFinalAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw PaymentException.badRequest("No payable amount — set service price in admin first");

        return b;
    }

    private void updateBookingMongo(String id, String chargeId, String qrUrl, String paymentStatus) {
        Query  q = Query.query(Criteria.where("_id").is(id));
        Update u = new Update()
            .set("paymentTransactionId", chargeId)
            .set("paymentStatus",        paymentStatus)
            .set("updatedAt",            Instant.now());
        if (qrUrl != null)              u.set("qrCodeUrl", qrUrl);
        if ("PAID".equals(paymentStatus)) u.set("paidAt", Instant.now());
        mongoTemplate.updateFirst(q, u, HospitalBookingDocument.class);
    }

    private void updateBookingStatus(String id, String status) {
        Query  q = Query.query(Criteria.where("_id").is(id));
        Update u = new Update().set("status", status).set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(q, u, HospitalBookingDocument.class);
    }

    private void upsertRecord(HospitalBookingDocument b, String chargeId, String method, String status) {
        HospitalPaymentRecord r = paymentRecordRepository.findByBookingNumber(b.getBookingNumber())
            .orElse(HospitalPaymentRecord.builder()
                .bookingNumber(b.getBookingNumber())
                .userId(b.getUserId())
                .patientName(b.getPatientName())
                .serviceName(b.getServiceName() != null ? b.getServiceName() : b.getPackageName())
                .amount(b.getFinalAmount())
                .paymentMethod(method)
                .build());

        r.setChargeId(chargeId);
        r.setPaymentStatus(status);
        if ("PAID".equals(status)) r.setPaidAt(Instant.now());
        paymentRecordRepository.save(r);
    }

    private void updateRecordStatus(String chargeId, String status, String omiseStatus, String failure) {
        paymentRecordRepository.findByChargeId(chargeId).ifPresent(r -> {
            r.setPaymentStatus(status);
            r.setOmiseStatus(omiseStatus);
            r.setFailureMessage(failure);
            if ("PAID".equals(status)) r.setPaidAt(Instant.now());
            paymentRecordRepository.save(r);
        });
    }

    /**
     * Synchronous version — used by syncPaymentStatus so NestJS can do the full
     * MongoDB update + rewards + audit log before we return the status to the client.
     */
    private void notifyBackendSync(String bookingNumber, String status, String chargeId) {
        try {
            WebClient.create(hospitalBackendUrl)
                .post()
                .uri("/bookings/payment-callback")
                .header("X-Internal-Secret", hospitalBackendSecret)
                .bodyValue(Map.of(
                    "bookingNumber", bookingNumber,
                    "status",        status,
                    "transactionId", chargeId
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block(); // blocking — we need NestJS to commit before we return PAID
            log.info("[Hospital] Sync notify: bookingNumber={} status={}", bookingNumber, status);
        } catch (Exception e) {
            // NestJS unreachable — fall back to direct MongoDB update so booking isn't stuck
            log.error("[Hospital] Sync notify failed, falling back to direct update bookingNumber={}", bookingNumber, e);
            bookingRepository.findByBookingNumber(bookingNumber).ifPresent(b -> {
                if ("SUCCESS".equals(status)) {
                    updateBookingMongo(b.getId(), chargeId, null, "PAID");
                    updateBookingStatus(b.getId(), "CONFIRMED");
                } else {
                    updateBookingMongo(b.getId(), chargeId, null, "FAILED");
                }
            });
        }
    }

    /** Best-effort async notification — used by webhook handler. */
    private void notifyBackend(String bookingNumber, String status, String chargeId) {
        try {
            WebClient.create(hospitalBackendUrl)
                .post()
                .uri("/bookings/payment-callback")
                .header("X-Internal-Secret", hospitalBackendSecret)
                .bodyValue(Map.of(
                    "bookingNumber",   bookingNumber,
                    "status",          status,
                    "transactionId",   chargeId
                ))
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                    r   -> log.info("[Hospital] Backend notified bookingNumber={} status={}", bookingNumber, status),
                    err -> log.error("[Hospital] Failed to notify backend bookingNumber={}", bookingNumber, err)
                );
        } catch (Exception e) {
            log.error("[Hospital] Error notifying backend bookingNumber={}", bookingNumber, e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractQrUrl(Map<String, Object> charge) {
        try {
            Map<String, Object> source = (Map<String, Object>) charge.get("source");
            if (source == null) return null;
            Map<String, Object> sc = (Map<String, Object>) source.get("scannable_code");
            if (sc == null) return null;
            Map<String, Object> img = (Map<String, Object>) sc.get("image");
            if (img == null) return null;
            return (String) img.get("download_uri");
        } catch (ClassCastException e) {
            log.warn("[Hospital] Could not extract QR URL from Omise charge", e);
            return null;
        }
    }

    private void verifySignature(byte[] rawBody, String signature) {
        if (signature == null || signature.isBlank()) return; // skip in dev
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                omiseConfig.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            ));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody));
            if (!expected.equals(signature)) throw PaymentException.badRequest("Invalid webhook signature");
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Signature verification failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(byte[] rawBody) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(rawBody, Map.class);
        } catch (Exception e) {
            throw PaymentException.badRequest("Invalid JSON body");
        }
    }
}
