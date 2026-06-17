package com.consoleshop.payment.hospital.domain.postgres;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Hospital payment audit record stored in PostgreSQL.
 * Uses snake_case table/column naming (Spring Boot default) so it does NOT
 * conflict with the Prisma-managed PascalCase tables.
 * JPA manages this table via ddl-auto — Prisma never touches it.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "hospital_payment_records", indexes = {
    @Index(name = "idx_hpr_booking_number", columnList = "booking_number"),
    @Index(name = "idx_hpr_charge_id",      columnList = "charge_id"),
    @Index(name = "idx_hpr_user_id",        columnList = "user_id"),
})
public class HospitalPaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "booking_number", nullable = false, unique = true, length = 32)
    private String bookingNumber;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "patient_name", length = 128)
    private String patientName;

    @Column(name = "service_name", length = 256)
    private String serviceName;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;   // PROMPTPAY | CARD

    @Column(name = "payment_status", length = 20, nullable = false)
    private String paymentStatus;   // PENDING | PAYMENT_PENDING | PAID | FAILED

    @Column(name = "charge_id", length = 64)
    private String chargeId;        // Omise charge ID (chrg_...)

    @Column(name = "omise_status", length = 32)
    private String omiseStatus;     // pending | successful | failed (raw from Omise)

    @Column(name = "failure_message", length = 512)
    private String failureMessage;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
