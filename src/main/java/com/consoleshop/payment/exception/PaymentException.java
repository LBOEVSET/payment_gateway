package com.consoleshop.payment.exception;

import org.springframework.http.HttpStatus;

public class PaymentException extends RuntimeException {

    private final HttpStatus status;

    public PaymentException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    // ── Factories ──────────────────────────────────────────────────────────────

    public static PaymentException notFound(String message) {
        return new PaymentException(message, HttpStatus.NOT_FOUND);
    }

    public static PaymentException badRequest(String message) {
        return new PaymentException(message, HttpStatus.BAD_REQUEST);
    }

    public static PaymentException forbidden(String message) {
        return new PaymentException(message, HttpStatus.FORBIDDEN);
    }

    public static PaymentException unauthorized(String message) {
        return new PaymentException(message, HttpStatus.UNAUTHORIZED);
    }

    public static PaymentException conflict(String message) {
        return new PaymentException(message, HttpStatus.CONFLICT);
    }
}
