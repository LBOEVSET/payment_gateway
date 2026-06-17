package com.consoleshop.payment.service;

import com.consoleshop.payment.config.OmiseConfig;
import com.consoleshop.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

/**
 * Thin wrapper around the Omise REST API.
 * Uses WebClient with Basic Auth (secretKey as username, empty password).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OmiseService {

    private final OmiseConfig omiseConfig;

    private WebClient buildClient() {
        String credentials = Base64.getEncoder()
                .encodeToString((omiseConfig.getSecretKey() + ":").getBytes());

        return WebClient.builder()
                .baseUrl(omiseConfig.getApiUrl())
                .defaultHeader("Authorization", "Basic " + credentials)
                .build();
    }

    /**
     * Handles Omise API error responses (4xx/5xx).
     * Omise always returns a JSON body with a "message" field even on errors,
     * so we read it and throw a PaymentException instead of a generic 500.
     */
    @SuppressWarnings("unchecked")
    private WebClient.ResponseSpec handleOmiseErrors(WebClient.ResponseSpec spec) {
        return spec.onStatus(
                status -> status.is4xxClientError() || status.is5xxServerError(),
                response -> response.bodyToMono(Map.class)
                        .flatMap(body -> {
                            String message = (String) body.getOrDefault("message", "Omise API error");
                            String code = (String) body.getOrDefault("code", "unknown");
                            log.error("Omise API error: code={} message={}", code, message);
                            return Mono.error(PaymentException.badRequest("Payment failed: " + message));
                        })
        );
    }

    /**
     * Creates a card charge via Omise.
     *
     * @param amountTHB amount in Thai Baht (will be converted to satang)
     * @param token     Omise card token
     * @param orderId   metadata reference
     * @return Omise charge response map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createCardCharge(double amountTHB, String token, String orderId) {
        return createCardCharge(amountTHB, token, orderId, null, null);
    }

    /**
     * Overload used by hospital payment service — adds metadata[provider] and metadata[bookingNumber]
     * so the webhook can route correctly between console shop and hospital.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createCardCharge(double amountTHB, String token, String reference,
                                                 String provider, String referenceKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(Math.round(amountTHB * 100)));
        form.add("currency", "thb");
        form.add("card", token);
        if (provider != null) {
            form.add("metadata[provider]", provider);
            form.add("metadata[" + (referenceKey != null ? referenceKey : "bookingNumber") + "]", reference);
        } else {
            form.add("metadata[orderId]", reference);
        }
        // return_uri is required for 3DS-enrolled cards. Omise ignores it for
        // non-3DS cards, so it is safe to always include it.
        form.add("return_uri", omiseConfig.getReturnUrl());

        return handleOmiseErrors(
                buildClient().post()
                        .uri("/charges")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData(form))
                        .retrieve()
        ).bodyToMono(Map.class).block();
    }

    /**
     * Creates a PromptPay charge via Omise.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createPromptPayCharge(double amountTHB, String orderId) {
        return createPromptPayCharge(amountTHB, orderId, null, null);
    }

    /**
     * Overload used by the hospital payment service.
     * Sets metadata[provider] and metadata[bookingNumber] so the webhook handler
     * can route the charge to the correct service.
     */
    public Map<String, Object> createPromptPayCharge(double amountTHB, String bookingNumber,
                                                      String provider, String referenceKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(Math.round(amountTHB * 100)));
        form.add("currency", "thb");
        if (provider != null) {
            form.add("metadata[provider]", provider);
            form.add("metadata[" + (referenceKey != null ? referenceKey : "bookingNumber") + "]", bookingNumber);
        } else {
            form.add("metadata[orderId]", bookingNumber);
        }
        form.add("source[type]", "promptpay");
        form.add("return_uri", omiseConfig.getReturnUrl());

        return handleOmiseErrors(
                buildClient().post()
                        .uri("/charges")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData(form))
                        .retrieve()
        ).bodyToMono(Map.class).block();
    }

    /**
     * Retrieves a charge by ID from Omise.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCharge(String chargeId) {
        return buildClient().get()
                .uri("/charges/{id}", chargeId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }
}
