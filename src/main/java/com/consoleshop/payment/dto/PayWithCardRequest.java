package com.consoleshop.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PayWithCardRequest {

    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotBlank(message = "token is required")
    private String token;
}
