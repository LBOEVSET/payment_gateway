package com.consoleshop.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Forwards Omise charge events to the Cinema BE when metadata.provider == "cinemaMax".
 *
 * The Cinema BE /internal/payment-hook endpoint:
 *  - verifies X-Internal-Secret
 *  - saves an invoice to MongoDB
 *  - confirms the booking
 *  - pushes a WebSocket notification to the frontend
 */
@Slf4j
@Service
public class CinemaForwarderService {

    @Value("${app.cinema-be-url}")
    private String cinemaBeUrl;

    @Value("${app.internal-secret}")
    private String internalSecret;

    private final WebClient webClient = WebClient.create();

    /**
     * Fire-and-forget POST of the Omise charge object to Cinema BE.
     * Uses reactive subscribe() so it does not block the webhook handler thread.
     */
    public void forward(Map<String, Object> charge) {
        String chargeId = String.valueOf(charge.get("id"));
        log.info("Forwarding charge {} to Cinema BE at {}", chargeId, cinemaBeUrl);

        webClient.post()
                .uri(cinemaBeUrl + "/internal/payment-hook")
                .header("Content-Type", "application/json")
                .header("X-Internal-Secret", internalSecret)
                .bodyValue(charge)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r ->
                        log.info("Cinema BE accepted charge {} — HTTP {}", chargeId, r.getStatusCode()))
                .doOnError(e ->
                        log.error("Failed to forward charge {} to Cinema BE: {}", chargeId, e.getMessage()))
                .subscribe();
    }
}
