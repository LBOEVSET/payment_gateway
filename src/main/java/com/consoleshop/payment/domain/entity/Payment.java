package com.consoleshop.payment.domain.entity;

import com.consoleshop.payment.domain.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Mirrors the Payment model in the shared PostgreSQL database.
 * The schema is owned by the NestJS backend via Prisma migrations;
 * this entity is READ/WRITE but never runs DDL.
 */
@Entity
@Table(name = "Payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "orderId", nullable = false, unique = true, length = 36)
    private String orderId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    // PostgreSQL stores this as a native enum type ("PaymentStatus"), not varchar.
    // @Enumerated(EnumType.STRING) sends a plain varchar which PostgreSQL rejects.
    // @JdbcTypeCode(SqlTypes.NAMED_ENUM) tells Hibernate to cast to the PG enum type.
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "chargeId")
    private String chargeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rawPayload", columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;

    @Column(name = "webhookProcessed", nullable = false)
    private Boolean webhookProcessed = false;

    @Column(name = "createdAt")
    private Instant createdAt = Instant.now();

    @Column(name = "updatedAt")
    private Instant updatedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orderId", insertable = false, updatable = false)
    private Order order;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
