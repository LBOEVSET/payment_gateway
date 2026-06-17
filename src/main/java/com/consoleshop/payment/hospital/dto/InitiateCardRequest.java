package com.consoleshop.payment.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InitiateCardRequest {
    @NotBlank(message = "bookingNumber is required")
    private String bookingNumber;

    @NotBlank(message = "omiseToken is required")
    private String omiseToken;
}
