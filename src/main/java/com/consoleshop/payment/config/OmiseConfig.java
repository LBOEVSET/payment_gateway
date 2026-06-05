package com.consoleshop.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "omise")
public class OmiseConfig {

    /** Omise secret key (skey_...) */
    private String secretKey;

    /** Omise public key (pkey_...) */
    private String publicKey;

    /** Webhook signing secret */
    private String webhookSecret;

    /** Base URL for Omise API */
    private String apiUrl = "https://api.omise.co";

    /** Return URL after 3DS / PromptPay redirect */
    private String returnUrl = "http://localhost:3022/payment-success";
}
