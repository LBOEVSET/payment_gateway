package com.consoleshop.payment.hospital.domain.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-write view of the hospital 'bookings' MongoDB collection.
 * Field names match the NestJS Mongoose schema exactly.
 */
@Data
@Document(collection = "bookings")
public class HospitalBookingDocument {

    @Id
    private String id;

    private String     bookingNumber;
    private String     type;            // HEALTH_PACKAGE | SERVICE_CONSULTATION
    private String     userId;
    private String     patientName;
    private String     patientPhone;
    private String     patientEmail;

    private String     packageId;
    private String     packageName;
    private String     serviceId;
    private String     serviceName;

    private Instant    appointmentDate;
    private String     appointmentTime;

    // Payment fields
    private String     paymentMethod;          // COUNTER | ONLINE
    private String     paymentStatus;          // PENDING | PAYMENT_PENDING | PAID | FAILED | REFUNDED
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String     paymentTransactionId;   // Omise charge ID
    private String     qrCodeUrl;              // PromptPay QR image URL from Omise
    private Instant    paidAt;

    // Booking status
    private String     status;  // PENDING | CONFIRMED | IN_PROGRESS | COMPLETED | CANCELLED

    private Instant    createdAt;
    private Instant    updatedAt;
}
