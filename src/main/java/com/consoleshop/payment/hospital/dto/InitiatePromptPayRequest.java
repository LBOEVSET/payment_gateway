package com.consoleshop.payment.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InitiatePromptPayRequest {
    @NotBlank(message = "bookingNumber is required")
    private String bookingNumber;
}
